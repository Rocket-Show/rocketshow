package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // The DMX universe used by the designer template
    private final static String UNIVERSE_UUID = "f869189e-4068-4800-a61f-66a1499946fd";

    @TempDir
    Path basePath;

    private static Path getCompatPath() throws Exception {
        return Path.of(DesignerProjectCompatibilityTest.class.getResource("/compat").toURI());
    }

    private static Path getDesignerProject(String version, String fileName) throws Exception {
        return getCompatPath().resolve(version).resolve("designer").resolve(fileName);
    }

    private Settings getSettings(Path projectBasePath) {
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setUuid(UNIVERSE_UUID);

        Settings settings = new Settings();
        settings.setBasePath(projectBasePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);
        settings.getLightingUniverseList().add(lightingUniverse);
        return settings;
    }

    // Load the project like the app does (from the designer directory) and return the DMX values the engine sends
    // at each position of the composition
    private List<Map<Integer, Integer>> play(Path projectFile, List<Long> positionMillisList) throws Exception {
        // Each project gets its own base directory with only this project in it
        Path projectBasePath = Files.createTempDirectory(basePath, "project");
        Path designerPath = projectBasePath.resolve("designer");
        Files.createDirectories(designerPath);
        Files.copy(projectFile, designerPath.resolve(projectFile.getFileName()));

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(getSettings(projectBasePath));

        FileFilterService fileFilterService = mock(FileFilterService.class);
        when(fileFilterService.filterFile(anyString())).thenReturn(false);

        LightingService lightingService = mock(LightingService.class);
        AtomicReference<LightingUniverseState> lightingUniverse = new AtomicReference<>();
        doAnswer(invocation -> {
            lightingUniverse.set(invocation.getArgument(0));
            return null;
        }).when(lightingService).addLightingUniverse(org.mockito.ArgumentMatchers.any());

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, fileFilterService, lightingService, new DefaultFixturePixelGroupResolveService());

        assertEquals(1, designerService.getAllProjects().size());
        Project project = designerService.getAllProjects().getFirst();

        List<Map<Integer, Integer>> universes = new ArrayList<>();

        for (long positionMillis : positionMillisList) {
            com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
            composition.setName(project.getCompositions()[0].getName());

            CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
            when(compositionPlayer.getComposition()).thenReturn(composition);
            when(compositionPlayer.getPositionMillis()).thenReturn(positionMillis);

            // Keep the first universe the engine sends
            CountDownLatch sent = new CountDownLatch(1);
            AtomicReference<Map<Integer, Integer>> universe = new AtomicReference<>();
            doAnswer(invocation -> {
                if (universe.get() == null) {
                    universe.set(new HashMap<>(lightingUniverse.get().getUniverse()));
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

    private Project readProject(Path projectFile) {
        return new tools.jackson.databind.ObjectMapper().readValue(projectFile.toFile(), Project.class);
    }

    // Load the project into the engine, which migrates it
    private void migrate(Project project) throws Exception {
        Path projectBasePath = Files.createTempDirectory(basePath, "project");

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(getSettings(projectBasePath));

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
        designerService.load(null, project, null);
        designerService.close();
    }

    private List<String> getFixtureUuids(List<PresetFixture> presetFixtures) {
        return presetFixtures.stream().map(PresetFixture::getFixtureUuid).toList();
    }

    // A position in the middle of each scene playback region of the project's first composition
    private List<Long> getScenePositions(Path projectFile) throws Exception {
        Project project = readProject(projectFile);
        List<Long> positionMillisList = new ArrayList<>();

        for (ScenePlaybackRegion region : project.getCompositions()[0].getScenePlaybackRegions()) {
            positionMillisList.add((region.getStartMillis() + region.getEndMillis()) / 2);
        }

        return positionMillisList;
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
            List<Long> positionMillisList = getScenePositions(projectFile);
            assertFalse(positionMillisList.isEmpty(), compatPath.relativize(projectFile) + " has no scene to play");

            for (Map<Integer, Integer> universe : play(projectFile, positionMillisList)) {
                assertNotNull(universe);
                assertTrue(universe.values().stream().anyMatch(value -> value > 0), compatPath.relativize(projectFile) + " produces no light");
            }
        }));
    }

    @Test
    void movesTheLookOfOldPresetsIntoTheirFirstStep() throws Exception {
        Project project = readProject(getDesignerProject("3.0.0", "Template version 2.json"));
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
        Project project = readProject(getDesignerProject("3.0.0", "Template version 7.json"));
        List<PresetStep> steps = project.getPresets().getFirst().getSteps();

        migrate(project);

        assertEquals(7, project.getVersion());
        assertEquals(steps, project.getPresets().getFirst().getSteps());
    }

    @Test
    void ordersOldPresetsAndFixturesLikeTheProject() throws Exception {
        // Until version 3, the project's preset list defined the layer order in a scene and its fixture list
        // the order a preset chases its fixtures in
        Path version2 = getDesignerProject("3.0.0", "Template version 2.json");
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
        Path version2 = getDesignerProject("3.0.0", "Template version 2.json");
        Path version7 = getDesignerProject("3.0.0", "Template version 7.json");

        // Before, at the start, in the middle and at the end of the scene, and after it
        List<Long> positionMillisList = List.of(1000L, 48801L, 63801L, 78800L, 90000L);

        List<Map<Integer, Integer>> version2Universes = play(version2, positionMillisList);
        List<Map<Integer, Integer>> version7Universes = play(version7, positionMillisList);

        assertEquals(version7Universes, version2Universes);
        assertTrue(version7Universes.get(2).values().stream().anyMatch(value -> value > 0));
    }

}
