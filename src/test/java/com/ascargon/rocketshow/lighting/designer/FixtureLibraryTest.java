package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Profiles of the fixture library the units download (src/test/resources/fixtures/): searching them like the designer
 * does and playing every mode of them.
 */
class FixtureLibraryTest {

    @TempDir
    Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path library;
    private DefaultFixtureService fixtureService;

    @BeforeEach
    void setUp() throws Exception {
        // The library in the fixture folder of a unit
        library = directory.resolve("fixtures");
        FileUtils.copyDirectory(new File(getClass().getResource("/fixtures").toURI()), library.toFile());

        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setFixturePath("fixtures");
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        fixtureService = new DefaultFixtureService(settingsService, mock(FileFilterService.class));
    }

    private List<String> profileUuids() throws Exception {
        try (Stream<Path> paths = Files.walk(library)) {
            return paths.filter(path -> path.toString().endsWith(".json") && !path.getFileName().toString().equals("manufacturers.json"))
                    .map(path -> library.relativize(path).toString().replace(".json", ""))
                    .sorted()
                    .toList();
        }
    }

    // A project with one fixture of the profile in the passed mode and a preset lighting it red, pointing it to the
    // middle
    private String project(String profileUuid, String modeShortName) throws Exception {
        ObjectNode profile = (ObjectNode) objectMapper.readTree(library.resolve(profileUuid + ".json").toFile());
        profile.put("uuid", profileUuid);

        ObjectNode project = objectMapper.createObjectNode();
        project.put("version", 7);
        project.put("name", "Library");
        project.putArray("fixtureProfiles").add(profile);

        ObjectNode fixture = project.putArray("fixtures").addObject();
        fixture.put("uuid", "fixture");
        fixture.put("name", "Fixture");
        fixture.put("profileUuid", profileUuid);
        fixture.put("modeShortName", modeShortName);
        fixture.put("dmxUniverseUuid", "universe");
        fixture.put("dmxFirstChannel", 0);
        project.putArray("presetFixtures").addObject().put("fixtureUuid", "fixture");

        ObjectNode preset = project.putArray("presets").addObject();
        preset.put("uuid", "preset");
        preset.put("name", "Preset");
        preset.putArray("fixtures").addObject().put("fixtureUuid", "fixture");
        preset.putArray("effects");
        ObjectNode step = preset.putArray("steps").addObject();
        step.put("uuid", "step");
        step.put("startMillis", 0);
        ArrayNode values = step.putArray("fixtureCapabilityValues");
        values.addObject().put("type", "Intensity").put("valuePercentage", 1.0);
        values.addObject().put("type", "ColorIntensity").put("color", "Red").put("valuePercentage", 1.0);
        values.addObject().put("type", "ColorIntensity").put("color", "Green").put("valuePercentage", 0.0);
        values.addObject().put("type", "ColorIntensity").put("color", "Blue").put("valuePercentage", 0.0);
        values.addObject().put("type", "Pan").put("valuePercentage", 0.5);
        values.addObject().put("type", "Tilt").put("valuePercentage", 0.5);
        step.putArray("fixtureChannelValues");
        step.putArray("effectAmounts");

        ObjectNode scene = project.putArray("scenes").addObject();
        scene.put("uuid", "scene");
        scene.put("name", "Scene");
        scene.putArray("presetUuids").add("preset");
        scene.put("fadeInMillis", 0);
        scene.put("fadeOutMillis", 0);

        ObjectNode composition = project.putArray("compositions").addObject();
        composition.put("uuid", "composition");
        composition.put("name", "Song");
        ObjectNode region = composition.putArray("scenePlaybackRegions").addObject();
        region.put("sceneUuid", "scene");
        region.put("startMillis", 0);
        region.put("endMillis", 60000);

        return project.toString();
    }

    private static String modeShortName(JsonNode mode) {
        return mode.has("shortName") ? mode.get("shortName").asString() : mode.get("name").asString();
    }

    @Test
    void listsEveryProfileOfTheLibrary() throws Exception {
        List<SearchFixtureTemplate> fixtures = fixtureService.searchFixtures(null, null, null, null);

        assertEquals(profileUuids(), fixtures.stream().map(SearchFixtureTemplate::getUuid).sorted().toList());

        SearchFixtureTemplate movingHead = fixtures.stream().filter(fixture -> fixture.getUuid().equals("stairville/mh-100")).findFirst().orElseThrow();
        assertEquals("MH-100", movingHead.getName());
        assertEquals("Stairville", movingHead.getManufacturerName());
        assertEquals("stairville", movingHead.getManufacturerShortName());
    }

    @Test
    void findsAProfileByItsUuid() throws Exception {
        assertEquals(List.of("stairville/mh-100"), fixtureService.searchFixtures("stairville/mh-100", null, null, null).stream().map(SearchFixtureTemplate::getUuid).toList());
        assertNull(fixtureService.searchFixtures("stairville/unknown", null, null, null));
    }

    @Test
    void readsAProfile() throws Exception {
        assertEquals("MH-100", objectMapper.readTree(fixtureService.getFixture("stairville/mh-100")).get("name").asString());
    }

    @TestFactory
    Stream<DynamicTest> playsEveryModeOfTheLibrary() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();

        for (String profileUuid : profileUuids()) {
            for (JsonNode mode : objectMapper.readTree(library.resolve(profileUuid + ".json").toFile()).get("modes")) {
                String modeShortName = modeShortName(mode);

                tests.add(DynamicTest.dynamicTest(profileUuid + " " + modeShortName, () -> {
                    DesignerTestEngine engine = new DesignerTestEngine();
                    engine.play(directory, project(profileUuid, modeShortName), "Song", 5000, List.of("universe"));

                    assertEquals(List.of(), engine.errors);
                }));
            }
        }

        return tests.stream();
    }

    @Test
    void keepsTheAddressesOfTheChannelsAfterUnusedOnes() throws Exception {
        // Hue, Hue fine, Saturation, Intensity, Strobe, Fan Control, -, Plus 7 Control, Red, -, Mint, Blue, Indigo, -, -
        Map<Integer, Integer> universe = new DesignerTestEngine().play(directory, project("etc/source-four-led-series-2-daylight-hd", "HSI+7"), "Song", 5000, List.of("universe")).get("universe");

        assertEquals(255, universe.get(4));
        assertEquals(255, universe.get(9));
        assertTrue(universe.getOrDefault(8, 0) < 255, "Plus 7 Control at " + universe.get(8));
    }

}
