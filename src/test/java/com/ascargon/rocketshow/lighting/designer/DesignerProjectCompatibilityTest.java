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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // The project version the designer of the web app (@rocketshow/designer) writes. Raise it with the version of
    // dist/designer_template.json, when the web app gets a designer writing a newer version.
    private static final int WEB_APP_DESIGNER_PROJECT_VERSION = 9;

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
            // Only the lighting: other tests leave apps running, e.g. trying to open the MIDI devices of their settings
            if (event.getLevel().isMoreSpecificThan(Level.ERROR) && event.getLoggerName().startsWith("com.ascargon.rocketshow.lighting")) {
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
                positions.add(new Position(composition.getName(), (long) ((region.getStartMillis() + region.getEndMillis()) / 2)));
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
            // Playing: the engine plays the scenes on the timeline, not the preset selected in the designer
            when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);

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

        // In the format of the designer the web app comes with, so a new project is not migrated in it. The engine may
        // know a newer one already, e.g. until that designer is released: then the engine migrates it like any other.
        assertEquals(WEB_APP_DESIGNER_PROJECT_VERSION, project.getVersion());
        assertTrue(WEB_APP_DESIGNER_PROJECT_VERSION <= DefaultDesignerService.SUPPORTED_PROJECT_VERSION);
        assertFalse(positions.isEmpty());

        List<String> errors = getErrorsWhilePlaying(template, positions, universes);

        assertTrue(errors.isEmpty(), "The designer template logged errors: " + errors);
        assertTrue(universes.stream().anyMatch(this::hasLight), "The designer template produces no light");
    }

    @Test
    void sendsTheSameLightWithTheTemplateOfTheVersionBefore() throws Exception {
        // The template was raised to version 8 by hand, like the engine migrates a project of version 7 (the copy in
        // src/test/resources/designer), and to version 9 (it has no pixels to rename). Both send the same light, also
        // while the curves of the template move.
        Path template = Path.of("dist", "designer_template.json");
        List<Position> positions = new ArrayList<>();

        for (Position position : getScenePositions(readProject(template))) {
            for (long offsetMillis : new long[]{0, 250, 700, 1300}) {
                positions.add(new Position(position.compositionName(), position.positionMillis() + offsetMillis));
            }
        }

        assertEquals(play(getMigrationProject("Template version 7.json"), positions), play(template, positions));
    }

    @Test
    void movesTheLookOfOldPresetsIntoTheirFirstStep() throws Exception {
        Project project = readProject(getMigrationProject("Template version 2.json"));
        Preset preset = project.getPresets().getFirst();
        List<FixtureCapabilityValue> look = preset.getFixtureCapabilityValues();
        assertTrue(preset.getSteps().isEmpty());

        migrate(project);

        assertEquals(DefaultDesignerService.SUPPORTED_PROJECT_VERSION, project.getVersion());
        assertEquals(1, preset.getSteps().size());
        assertEquals(look, preset.getSteps().getFirst().getFixtureCapabilityValues());
    }

    @Test
    void keepsTheStepsOfCurrentProjects() throws Exception {
        Project project = readProject(getMigrationProject("Template version 7.json"));
        List<PresetStep> steps = project.getPresets().getFirst().getSteps();

        migrate(project);

        assertEquals(DefaultDesignerService.SUPPORTED_PROJECT_VERSION, project.getVersion());
        assertEquals(steps, project.getPresets().getFirst().getSteps());
    }

    @Test
    void swingsTheCurvesOfOldProjectsLikeBefore() throws Exception {
        // Until version 8, the curves except the square swung over half of their amplitude
        Project project = readProject(getMigrationProject("Template version 7.json"));
        List<Effect> effects = project.getPresets().getFirst().getEffects();
        EffectCurve sine = (EffectCurve) effects.getFirst();
        double sineAmplitude = sine.getAmplitude();
        EffectCurve square = new EffectCurve();
        square.setCurveType("square");
        square.setAmplitude(0.5f);
        effects.add(square);

        migrate(project);

        assertEquals(DefaultDesignerService.SUPPORTED_PROJECT_VERSION, project.getVersion());
        assertEquals(sineAmplitude / 2, sine.getAmplitude());
        assertEquals(0.5f, square.getAmplitude());
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

    private static List<String> getFixturesAndPixels(List<PresetFixture> presetFixtures) {
        return presetFixtures.stream().map(presetFixture -> presetFixture.getFixtureUuid() + (presetFixture.getPixelKey() == null ? "" : " " + presetFixture.getPixelKey())).toList();
    }

    @Test
    void listsTheFixturesOfAProjectOfVersion1WithTheirPixels() throws Exception {
        // Before version 2, a preset (and the project) named its fixtures only. Since, a fixture and each of its pixels
        // can be chosen on their own: a fixture of version 1 means all of it.
        Project project = DesignerJson.MAPPER.readValue("""
                {"version": 1, "name": "Pixels",
                 "fixtureProfiles": [{"uuid": "test/bar", "name": "Bar", "matrix": {"pixelCount": [2, 1, 1]},
                   "availableChannels": {"Dimmer": {"capability": {"type": "Intensity"}}},
                   "templateChannels": {"Red $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Red"}}},
                   "modes": [{"name": "3-channel", "shortName": "3ch", "channels": ["Dimmer",
                     {"insert": "matrixChannels", "repeatFor": "eachPixelABC", "channelOrder": "perPixel", "templateChannels": ["Red $pixelKey"]}]}]}],
                 "fixtures": [{"uuid": "bar", "name": "Bar", "profileUuid": "test/bar", "modeShortName": "3ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 0}],
                 "presets": [{"uuid": "preset", "name": "Preset", "fixtureUuids": ["bar"]}],
                 "scenes": [], "compositions": []}
                """, Project.class);

        migrate(project);

        Preset preset = project.getPresets().getFirst();
        assertEquals(List.of("bar", "bar 1", "bar 2"), getFixturesAndPixels(project.getPresetFixtures()));
        assertEquals(List.of("bar", "bar 1", "bar 2"), getFixturesAndPixels(preset.getFixtures()));
        assertNull(preset.getFixtureUuids());
    }

    @Test
    void movesTheChannelValuesOfOldPresetsIntoTheirFirstStep() throws Exception {
        Project project = DesignerJson.MAPPER.readValue("""
                {"version": 2, "name": "Strobe", "fixtures": [], "presetFixtures": [], "scenes": [], "compositions": [],
                 "presets": [{"uuid": "preset", "name": "Preset", "fixtures": [],
                   "fixtureChannelValues": [{"profileUuid": "test/rgb-par", "channelName": "Strobe", "value": 20}]}]}
                """, Project.class);
        List<FixtureChannelValue> look = project.getPresets().getFirst().getFixtureChannelValues();

        migrate(project);

        assertEquals(look, project.getPresets().getFirst().getSteps().getFirst().getFixtureChannelValues());
    }

    @Test
    void keepsTheOrdersOfAProjectOfVersion3() throws Exception {
        // Since version 3, a scene layers its presets and a preset may chase its fixtures in an order of its own
        Project project = DesignerJson.MAPPER.readValue("""
                {"version": 3, "name": "Orders", "fixtures": [], "compositions": [],
                 "presetFixtures": [{"fixtureUuid": "a"}, {"fixtureUuid": "b"}],
                 "presets": [{"uuid": "first", "name": "First", "useGlobalFixtureOrder": false, "fixtures": [{"fixtureUuid": "b"}, {"fixtureUuid": "a"}]},
                             {"uuid": "second", "name": "Second", "fixtures": []}],
                 "scenes": [{"uuid": "scene", "name": "Scene", "presetUuids": ["second", "first"]}]}
                """, Project.class);

        migrate(project);

        Preset first = project.getPresets().getFirst();
        assertArrayEquals(new String[]{"second", "first"}, project.getScenes().getFirst().getPresetUuids());
        assertEquals(List.of("b", "a"), getFixtureUuids(first.getFixtures()));
        assertFalse(first.isUseGlobalFixtureOrder());
    }

    @Test
    void ordersAFixtureTheOldProjectDoesNotListFirst() throws Exception {
        // like the designer, which finds it at no place of the list of the project
        Project project = DesignerJson.MAPPER.readValue("""
                {"version": 2, "name": "Orders", "fixtures": [], "scenes": [], "compositions": [],
                 "presetFixtures": [{"fixtureUuid": "a"}, {"fixtureUuid": "b"}],
                 "presets": [{"uuid": "preset", "name": "Preset", "fixtures": [{"fixtureUuid": "b"}, {"fixtureUuid": "a"}, {"fixtureUuid": "gone"}]}]}
                """, Project.class);

        migrate(project);

        assertEquals(List.of("gone", "a", "b"), getFixtureUuids(project.getPresets().getFirst().getFixtures()));
    }

    // A project of the passed version with a plane of 3 by 2 pixels given by their count, its pixels named like before
    // version 9 ("Pixel 3-2-1"): the ones of the plane and some beside it
    private static Project projectWithOldPixelKeys(int version) {
        return DesignerJson.MAPPER.readValue("""
                {"version": %d, "name": "Pixels", "scenes": [], "compositions": [],
                 "fixtureProfiles": [{"uuid": "test/plane", "name": "Plane", "matrix": {"pixelCount": [3, 2, 1]},
                   "availableChannels": {"Dimmer": {"capability": {"type": "Intensity"}}},
                   "templateChannels": {"Red $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Red"}}},
                   "modes": [{"name": "7-channel", "shortName": "7ch", "channels": ["Dimmer",
                     {"insert": "matrixChannels", "repeatFor": "eachPixelABC", "channelOrder": "perPixel", "templateChannels": ["Red $pixelKey"]}]}]}],
                 "fixtures": [{"uuid": "plane", "name": "Plane", "profileUuid": "test/plane", "modeShortName": "7ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 0}],
                 "presetFixtures": [{"fixtureUuid": "plane", "pixelKey": "Pixel 1-1-1"}, {"fixtureUuid": "plane", "pixelKey": "Pixel 3-2-1"},
                   {"fixtureUuid": "plane", "pixelKey": "Pixel 0-1-1"}, {"fixtureUuid": "plane", "pixelKey": "Pixel 4-1-1"},
                   {"fixtureUuid": "plane", "pixelKey": "Pixel 1-0-1"}, {"fixtureUuid": "plane", "pixelKey": "Pixel 1-3-1"},
                   {"fixtureUuid": "plane", "pixelKey": "Pixel 1-1-0"}, {"fixtureUuid": "plane", "pixelKey": "Pixel 1-1-2"}],
                 "presets": [{"uuid": "preset", "name": "Preset", "fixtures": [{"fixtureUuid": "plane", "pixelKey": "Pixel 3-2-1"}],
                   "steps": [{"uuid": "step", "fixtureChannelValues": [{"profileUuid": "test/plane", "channelName": "Red Pixel 3-2-1", "value": 255}]}],
                   "effects": [{"type": "curve", "uuid": "curve", "capabilities": [],
                     "channels": [{"profileUuid": "test/plane", "channels": ["Red Pixel 2-1-1", "Red Pixel 4-1-1", "Dimmer"]}]}]}]}
                """.formatted(version), Project.class);
    }

    @Test
    void renamesThePixelsOfOlderProjectsLikeTheFixtureLibraryNamesThem() throws Exception {
        Project project = projectWithOldPixelKeys(8);

        migrate(project);

        // Inside the plane only
        assertEquals(List.of("plane (1, 1)", "plane (3, 2)", "plane Pixel 0-1-1", "plane Pixel 4-1-1", "plane Pixel 1-0-1", "plane Pixel 1-3-1",
                "plane Pixel 1-1-0", "plane Pixel 1-1-2"), getFixturesAndPixels(project.getPresetFixtures()));

        Preset preset = project.getPresets().getFirst();
        assertEquals(List.of("plane (3, 2)"), getFixturesAndPixels(preset.getFixtures()));
        assertEquals("Red (3, 2)", preset.getSteps().getFirst().getFixtureChannelValues().getFirst().getChannelName());
        EffectCurve curve = (EffectCurve) preset.getEffects().getFirst();
        assertEquals(List.of("Red (2, 1)", "Red Pixel 4-1-1", "Dimmer"), curve.getChannels().getFirst().getChannels());
    }

    @Test
    void keepsTheNameOfAPixelWithANumberTooLongForAnInt() throws Exception {
        // like the designer, which reads it as a number far beside the matrix: the project is loaded all the same
        Project project = projectWithOldPixelKeys(8);
        project.getPresetFixtures().getFirst().setPixelKey("Pixel 99999999999-1-1");
        project.getPresets().getFirst().getSteps().getFirst().getFixtureChannelValues().getFirst().setChannelName("Red Pixel 1-99999999999-1");

        migrate(project);

        assertEquals("plane Pixel 99999999999-1-1", getFixturesAndPixels(project.getPresetFixtures()).getFirst());
        assertEquals("Red Pixel 1-99999999999-1", project.getPresets().getFirst().getSteps().getFirst().getFixtureChannelValues().getFirst().getChannelName());
    }

    @Test
    void renamesThePixelsOnlyOnce() throws Exception {
        // In a project of version 9, a name looking like an old one is a name of its own (e.g. of a pixel group)
        Project project = projectWithOldPixelKeys(9);

        migrate(project);

        assertEquals("plane Pixel 1-1-1", getFixturesAndPixels(project.getPresetFixtures()).getFirst());
        assertEquals("Red Pixel 3-2-1", project.getPresets().getFirst().getSteps().getFirst().getFixtureChannelValues().getFirst().getChannelName());
    }

    @Test
    void warnsAboutAProjectOfANewerDesigner() throws Throwable {
        Project newer = readProject(getMigrationProject("Template version 7.json"));
        newer.setVersion(DefaultDesignerService.SUPPORTED_PROJECT_VERSION + 1);
        Project current = readProject(getMigrationProject("Template version 7.json"));
        current.setVersion(DefaultDesignerService.SUPPORTED_PROJECT_VERSION);

        List<String> warnings = DesignerTestEngine.logged(Level.WARN, () -> migrate(newer));

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("newer designer"), warnings.getFirst());
        assertEquals(List.of(), DesignerTestEngine.logged(Level.WARN, () -> migrate(current)));
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
