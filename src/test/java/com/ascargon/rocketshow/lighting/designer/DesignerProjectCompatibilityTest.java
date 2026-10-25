package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plays the designer projects of earlier releases (src/test/resources/compat/&lt;version&gt;/designer/) with the
 * current engine. Older projects are migrated when they are loaded and must still produce the same light.
 */
class DesignerProjectCompatibilityTest {

    // A position in a composition of the project
    private record Position(String compositionName, long positionMillis) {
    }

    // Collects the errors logged while playing (e.g. when the engine can't calculate the universe)
    private static class ErrorCollector extends AbstractAppender {

        private final List<String> errors = new CopyOnWriteArrayList<>();

        ErrorCollector() {
            super("DesignerProjectCompatibilityTest", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
                errors.add(event.getMessage().getFormattedMessage());
            }
        }

    }

    @TempDir
    Path basePath;

    private static Path getCompatPath() throws Exception {
        return Path.of(DesignerProjectCompatibilityTest.class.getResource("/compat").toURI());
    }

    // Projects in an older and the current format, for the migrations
    private static Path getMigrationProject(String fileName) throws Exception {
        return Path.of(DesignerProjectCompatibilityTest.class.getResource("/designer").toURI()).resolve(fileName);
    }

    private Project readProject(Path projectFile) {
        return new tools.jackson.databind.ObjectMapper().readValue(projectFile.toFile(), Project.class);
    }

    // The settings of a device with a DMX universe for each universe used in the project
    private Settings getSettings(Path projectBasePath, Project project) {
        Settings settings = new Settings();
        settings.setBasePath(projectBasePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);

        Set<String> universeUuids = new LinkedHashSet<>();
        if (project.getFixtures() != null) {
            for (Fixture fixture : project.getFixtures()) {
                universeUuids.add(fixture.getDmxUniverseUuid());
            }
        }

        for (String universeUuid : universeUuids) {
            LightingUniverse lightingUniverse = new LightingUniverse();
            lightingUniverse.setUuid(universeUuid);
            settings.getLightingUniverseList().add(lightingUniverse);
        }

        return settings;
    }

    // The middle of each scene playback region of each composition in the project
    private List<Position> getScenePositions(Project project) {
        List<Position> positions = new ArrayList<>();

        if (project.getCompositions() == null) {
            return positions;
        }

        for (Composition composition : project.getCompositions()) {
            if (composition.getScenePlaybackRegions() == null) {
                continue;
            }

            for (ScenePlaybackRegion region : composition.getScenePlaybackRegions()) {
                positions.add(new Position(composition.getName(), (region.getStartMillis() + region.getEndMillis()) / 2));
            }
        }

        return positions;
    }

    // Load the project like the app does (from the designer directory) and return the DMX values the engine sends
    // at each position, by universe and channel (e.g. "<universe uuid>/0")
    private List<Map<String, Integer>> play(Path projectFile, List<Position> positions) throws Exception {
        // Each project gets its own base directory with only this project in it
        Path projectBasePath = Files.createTempDirectory(basePath, "project");
        Path designerPath = projectBasePath.resolve("designer");
        Files.createDirectories(designerPath);
        Files.copy(projectFile, designerPath.resolve(projectFile.getFileName()));

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(getSettings(projectBasePath, readProject(projectFile)));

        FileFilterService fileFilterService = mock(FileFilterService.class);
        when(fileFilterService.filterFile(anyString())).thenReturn(false);

        LightingService lightingService = mock(LightingService.class);
        List<LightingUniverseState> lightingUniverses = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> lightingUniverses.add(invocation.getArgument(0))).when(lightingService).addLightingUniverse(any());
        doAnswer(invocation -> lightingUniverses.remove((LightingUniverseState) invocation.getArgument(0))).when(lightingService).removeLightingUniverse(any());

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, fileFilterService, lightingService, new DefaultFixturePixelGroupResolveService());

        assertEquals(1, designerService.getAllProjects().size());
        Project project = designerService.getAllProjects().getFirst();

        List<Map<String, Integer>> universes = new ArrayList<>();

        for (Position position : positions) {
            com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
            composition.setName(position.compositionName());

            CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
            when(compositionPlayer.getComposition()).thenReturn(composition);
            when(compositionPlayer.getPositionMillis()).thenReturn(position.positionMillis());

            // Keep the first values the engine sends
            CountDownLatch sent = new CountDownLatch(1);
            AtomicReference<Map<String, Integer>> universe = new AtomicReference<>();
            doAnswer(invocation -> {
                if (universe.get() == null) {
                    Map<String, Integer> values = new HashMap<>();
                    for (LightingUniverseState lightingUniverse : lightingUniverses) {
                        lightingUniverse.getUniverse().forEach((channel, value) -> values.put(lightingUniverse.getMappingUuid() + "/" + channel, value));
                    }
                    universe.set(values);
                    sent.countDown();
                }
                return null;
            }).when(lightingService).sendExternalSync(anyBoolean());

            designerService.load(compositionPlayer, project, null);
            designerService.play();

            assertTrue(sent.await(10, TimeUnit.SECONDS), "The engine did not send the universe");
            designerService.close();

            universes.add(universe.get());
        }

        return universes;
    }

    // Play while collecting the logged errors
    private List<String> getErrorsWhilePlaying(Path projectFile, List<Position> positions, List<Map<String, Integer>> universes) throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        ErrorCollector errorCollector = new ErrorCollector();
        errorCollector.start();
        context.getConfiguration().getRootLogger().addAppender(errorCollector, null, null);
        context.updateLoggers();

        try {
            universes.addAll(play(projectFile, positions));
        } finally {
            context.getConfiguration().getRootLogger().removeAppender(errorCollector.getName());
            context.updateLoggers();
            errorCollector.stop();
        }

        return errorCollector.errors;
    }

    // Load the project into the engine, which migrates it
    private void migrate(Project project) throws Exception {
        Path projectBasePath = Files.createTempDirectory(basePath, "project");

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(getSettings(projectBasePath, project));

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
        designerService.load(null, project, null);
        designerService.close();
    }

    private List<String> getFixtureUuids(List<PresetFixture> presetFixtures) {
        return presetFixtures.stream().map(PresetFixture::getFixtureUuid).toList();
    }

    private boolean hasLight(Map<String, Integer> universe) {
        return universe.values().stream().anyMatch(value -> value > 0);
    }

    @TestFactory
    Stream<DynamicTest> playsTheDesignerProjectsOfEarlierReleases() throws Exception {
        Path compatPath = getCompatPath();

        List<Path> projectFiles;
        try (Stream<Path> paths = Files.walk(compatPath)) {
            projectFiles = paths
                    .filter(path -> path.getParent().getFileName().toString().equals("designer"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }

        assertFalse(projectFiles.isEmpty());

        return projectFiles.stream().map(projectFile -> DynamicTest.dynamicTest(compatPath.relativize(projectFile).toString(), () -> {
            Path relativePath = compatPath.relativize(projectFile);
            List<Position> positions = getScenePositions(readProject(projectFile));
            List<Map<String, Integer>> universes = new ArrayList<>();

            List<String> errors = getErrorsWhilePlaying(projectFile, positions, universes);

            assertTrue(errors.isEmpty(), relativePath + " logged errors: " + errors);

            // A single scene may be dark on purpose (e.g. a blackout), but not the whole project
            if (!positions.isEmpty()) {
                assertTrue(universes.stream().anyMatch(this::hasLight), relativePath + " produces no light");
            }
        }));
    }

    @Test
    void playsTheTemplateOfNewProjects() throws Exception {
        // A new project starts with the designer template of the latest release (see SeedFilesTest)
        Path template = Path.of("dist", "designer_template.json");
        Project project = readProject(template);
        List<Position> positions = getScenePositions(project);
        List<Map<String, Integer>> universes = new ArrayList<>();

        // In the current format, a new project is not migrated
        assertEquals(DefaultDesignerService.SUPPORTED_PROJECT_VERSION, project.getVersion());
        assertFalse(positions.isEmpty());

        List<String> errors = getErrorsWhilePlaying(template, positions, universes);

        assertTrue(errors.isEmpty(), "The designer template logged errors: " + errors);
        assertTrue(universes.stream().anyMatch(this::hasLight), "The designer template produces no light");
    }

    @Test
    void movesTheLookOfOldPresetsIntoTheirFirstStep() throws Exception {
        Project project = readProject(getMigrationProject("Template version 2.json"));
        Preset preset = project.getPresets().getFirst();
        List<FixtureCapabilityValue> look = preset.getFixtureCapabilityValues();
        assertTrue(preset.getSteps().isEmpty());

        migrate(project);

        assertEquals(7, project.getVersion());
        assertEquals(1, preset.getSteps().size());
        assertEquals(look, preset.getSteps().getFirst().getFixtureCapabilityValues());
    }

    @Test
    void keepsTheStepsOfCurrentProjects() throws Exception {
        Project project = readProject(getMigrationProject("Template version 7.json"));
        List<PresetStep> steps = project.getPresets().getFirst().getSteps();

        migrate(project);

        assertEquals(7, project.getVersion());
        assertEquals(steps, project.getPresets().getFirst().getSteps());
    }

    @Test
    void ordersOldPresetsAndFixturesLikeTheProject() throws Exception {
        // Until version 3, the project's preset list defined the layer order in a scene and its fixture list
        // the order a preset chases its fixtures in
        Path version2 = getMigrationProject("Template version 2.json");
        Project project = readProject(version2);
        Preset firstPreset = project.getPresets().getFirst();

        Preset secondPreset = readProject(version2).getPresets().getFirst();
        secondPreset.setUuid("second-preset");
        project.getPresets().add(secondPreset);

        Scene scene = project.getScenes().getFirst();
        scene.setPresetUuids(new String[]{"second-preset", firstPreset.getUuid()});
        Collections.reverse(firstPreset.getFixtures());

        migrate(project);

        assertArrayEquals(new String[]{firstPreset.getUuid(), "second-preset"}, scene.getPresetUuids());
        assertEquals(getFixtureUuids(project.getPresetFixtures()), getFixtureUuids(firstPreset.getFixtures()));
    }

    @Test
    void migratedTemplateLooksLikeTheCurrentTemplate() throws Exception {
        // The template before and after the preset steps (project version 2 and 7) define the same show
        Path version2 = getMigrationProject("Template version 2.json");
        Path version7 = getMigrationProject("Template version 7.json");

        // Before, at the start, in the middle and at the end of the scene, and after it
        List<Position> positions = new ArrayList<>();
        for (long positionMillis : List.of(1000L, 48801L, 63801L, 78800L, 90000L)) {
            positions.add(new Position("Example Composition", positionMillis));
        }

        List<Map<String, Integer>> version2Universes = play(version2, positions);
        List<Map<String, Integer>> version7Universes = play(version7, positions);

        assertEquals(version7Universes, version2Universes);
        assertTrue(hasLight(version7Universes.get(2)));
    }

}
