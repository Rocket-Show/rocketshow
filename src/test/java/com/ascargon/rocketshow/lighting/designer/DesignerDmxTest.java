package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DMX values the designer engine sends for the fixtures of a project: the channels of a preset, fine channels,
 * default values, color wheels and several fixtures and universes.
 */
class DesignerDmxTest {

    private static final String FRONT = "universe-front";
    private static final String BACK = "universe-back";

    // An RGB par with an unused channel, a moving head with 16 bit pan and tilt and a color wheel. Par 1 starts at
    // DMX address 1 of the front universe, par 2 at address 11, the moving head at address 1 of the back universe.
    private static final String PROJECT = """
            {
              "version": 7,
              "name": "DMX test",
              "masterDimmerValue": 1,
              "fixtureProfiles": [
                {
                  "uuid": "test/rgb-par",
                  "name": "RGB par",
                  "availableChannels": {
                    "Dimmer": {"capability": {"type": "Intensity"}},
                    "Red": {"capability": {"type": "ColorIntensity", "color": "Red"}},
                    "Green": {"capability": {"type": "ColorIntensity", "color": "Green"}},
                    "Blue": {"capability": {"type": "ColorIntensity", "color": "Blue"}},
                    "Strobe": {"defaultValue": 5, "capabilities": [
                      {"dmxRange": [0, 9], "type": "ShutterStrobe", "shutterEffect": "Open"},
                      {"dmxRange": [10, 255], "type": "ShutterStrobe", "shutterEffect": "Strobe"}
                    ]}
                  },
                  "modes": [{"name": "6-channel", "shortName": "6ch", "channels": ["Dimmer", "Red", "Green", "Blue", null, "Strobe"]}]
                },
                {
                  "uuid": "test/moving-head",
                  "name": "Moving head",
                  "availableChannels": {
                    "Pan": {"fineChannelAliases": ["Pan fine"], "defaultValue": "50%", "capability": {"type": "Pan", "angleStart": "0deg", "angleEnd": "540deg"}},
                    "Tilt": {"fineChannelAliases": ["Tilt fine"], "defaultValue": "12.5%", "capability": {"type": "Tilt", "angleStart": "0deg", "angleEnd": "270deg"}},
                    "Dimmer": {"capability": {"type": "Intensity"}},
                    "Color Wheel": {"capabilities": [
                      {"dmxRange": [0, 9], "type": "WheelSlot", "slotNumber": 1},
                      {"dmxRange": [10, 19], "type": "WheelSlot", "slotNumber": 2},
                      {"dmxRange": [20, 29], "type": "WheelSlot", "slotNumber": 3}
                    ]}
                  },
                  "wheels": {
                    "Color Wheel": {"slots": [{"type": "Open"}, {"type": "Color", "colors": ["#ff0000"]}, {"type": "Color", "colors": ["#0000ff"]}]}
                  },
                  "modes": [{"name": "6-channel", "shortName": "6ch", "channels": ["Pan", "Pan fine", "Tilt", "Tilt fine", "Dimmer", "Color Wheel"]}]
                }
              ],
              "fixtures": [
                {"uuid": "par-1", "name": "Par 1", "profileUuid": "test/rgb-par", "modeShortName": "6ch", "dmxUniverseUuid": "universe-front", "dmxFirstChannel": 0},
                {"uuid": "par-2", "name": "Par 2", "profileUuid": "test/rgb-par", "modeShortName": "6ch", "dmxUniverseUuid": "universe-front", "dmxFirstChannel": 10},
                {"uuid": "head", "name": "Head", "profileUuid": "test/moving-head", "modeShortName": "6ch", "dmxUniverseUuid": "universe-back", "dmxFirstChannel": 0}
              ],
              "presetFixtures": [{"fixtureUuid": "par-1"}, {"fixtureUuid": "par-2"}, {"fixtureUuid": "head"}],
              "presets": [],
              "scenes": [],
              "compositions": [{"uuid": "song", "name": "Song", "scenePlaybackRegions": []}]
            }
            """;

    @TempDir
    Path directory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // The project as the designer saves it: every channel of a mode is an object, unused ones without a name
    private ObjectNode project() {
        ObjectNode project = rawProject();

        for (var profile : project.get("fixtureProfiles")) {
            for (var channel : profile.get("availableChannels")) {
                if (!channel.has("fineChannelAliases")) {
                    ((ObjectNode) channel).putArray("fineChannelAliases");
                }
            }

            for (var mode : profile.get("modes")) {
                ArrayNode savedChannels = objectMapper.createArrayNode();

                for (var channel : mode.get("channels")) {
                    ObjectNode savedChannel = savedChannels.addObject();
                    if (!channel.isNull()) {
                        savedChannel.put("name", channel.asString());
                    }
                    savedChannel.putArray("repeatFor");
                    savedChannel.putArray("templateChannels");
                }

                ((ObjectNode) mode).set("channels", savedChannels);
            }
        }

        return project;
    }

    // The profiles as the fixture library has them
    private ObjectNode rawProject() {
        return (ObjectNode) objectMapper.readTree(PROJECT);
    }

    private static ObjectNode capability(ObjectNode values, String type, Double valuePercentage) {
        ObjectNode capability = values.objectNode();
        capability.put("type", type);
        if (valuePercentage != null) {
            capability.put("valuePercentage", valuePercentage);
        }
        return capability;
    }

    // Add a preset for the passed fixtures with the passed capability values, played by a scene on the whole timeline
    private void addPreset(ObjectNode project, String name, List<String> fixtureUuids, List<ObjectNode> capabilityValues) {
        ObjectNode preset = ((ArrayNode) project.get("presets")).addObject();
        preset.put("uuid", name);
        preset.put("name", name);
        ArrayNode fixtures = preset.putArray("fixtures");
        fixtureUuids.forEach(fixtureUuid -> fixtures.addObject().put("fixtureUuid", fixtureUuid));
        preset.putArray("fixtureCapabilityValues");
        preset.putArray("fixtureChannelValues");
        preset.putArray("effects");

        ObjectNode step = preset.putArray("steps").addObject();
        step.put("uuid", name + "-step");
        step.put("startMillis", 0);
        step.putArray("fixtureCapabilityValues").addAll(capabilityValues);
        step.putArray("fixtureChannelValues");
        step.putArray("effectAmounts");

        ObjectNode scene = ((ArrayNode) project.get("scenes")).addObject();
        scene.put("uuid", name + "-scene");
        scene.put("name", name);
        scene.putArray("presetUuids").add(name);
        scene.put("fadeInMillis", 0);
        scene.put("fadeOutMillis", 0);

        ObjectNode region = ((ArrayNode) project.get("compositions").get(0).get("scenePlaybackRegions")).addObject();
        region.put("sceneUuid", name + "-scene");
        region.put("startMillis", 0);
        region.put("endMillis", 60000);
    }

    // Play the composition of the project like the app does and return the first DMX values the engine sends, by
    // universe and DMX address (1 to 512)
    private Map<String, Map<Integer, Integer>> play(ObjectNode project) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        Map<String, Map<Integer, Integer>> universes = engine.play(directory, project.toString(), "Song", 5000, List.of(FRONT, BACK));

        assertEquals(List.of(), engine.errors);
        return universes;
    }

    private static List<Integer> addresses(Map<Integer, Integer> universe, int first, int last) {
        return java.util.stream.IntStream.rangeClosed(first, last).mapToObj(address -> universe.getOrDefault(address, 0)).toList();
    }

    @Test
    void sendsTheDefaultValuesWithoutPreset() throws Exception {
        Map<String, Map<Integer, Integer>> universes = play(project());

        // Par: strobe open (DMX 5)
        assertEquals(List.of(0, 0, 0, 0, 0, 5), addresses(universes.get(FRONT), 1, 6));

        // Head: pan 50 % and tilt 12.5 % of 16 bit
        List<Integer> head = addresses(universes.get(BACK), 1, 6);
        assertEquals(32767, head.get(0) * 256 + head.get(1), 1);
        assertEquals(8191, head.get(2) * 256 + head.get(3), 1);
        assertEquals(List.of(0, 0), head.subList(4, 6));
    }

    @Test
    void setsTheDimmerAndColorOfEveryFixtureOfAPreset() throws Exception {
        ObjectNode project = project();
        ObjectNode red = capability(project, "ColorIntensity", 1.0);
        red.put("color", "Red");
        ObjectNode blue = capability(project, "ColorIntensity", 0.2);
        blue.put("color", "Blue");
        addPreset(project, "Red", List.of("par-1", "par-2"), List.of(capability(project, "Intensity", 0.5), red, blue));

        Map<Integer, Integer> front = play(project).get(FRONT);

        assertEquals(List.of(127, 255, 0, 51, 0, 5), addresses(front, 1, 6));
        assertEquals(List.of(127, 255, 0, 51, 0, 5), addresses(front, 11, 16));
    }

    @Test
    void pointsAMovingHeadWithFineChannels() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Position", List.of("head"), List.of(capability(project, "Pan", 1.0), capability(project, "Tilt", 0.25), capability(project, "Intensity", 1.0)));

        List<Integer> head = addresses(play(project).get(BACK), 1, 5);

        assertEquals(List.of(255, 255), head.subList(0, 2));
        assertEquals(16383, head.get(2) * 256 + head.get(3), 1);
        assertEquals(255, head.get(4));
    }

    @Test
    void picksTheClosestColorOfTheWheel() throws Exception {
        ObjectNode project = project();
        ObjectNode red = capability(project, "ColorIntensity", 0.0);
        red.put("color", "Red");
        ObjectNode green = capability(project, "ColorIntensity", 0.1);
        green.put("color", "Green");
        ObjectNode blue = capability(project, "ColorIntensity", 0.9);
        blue.put("color", "Blue");
        addPreset(project, "Blue", List.of("head"), List.of(capability(project, "Intensity", 1.0), red, green, blue));

        // The blue slot (DMX 20 to 29)
        int colorWheel = play(project).get(BACK).getOrDefault(6, 0);
        assertTrue(colorWheel >= 20 && colorWheel <= 29, "Color wheel at " + colorWheel);
    }

    @Test
    void setsAWheelSlotDirectly() throws Exception {
        ObjectNode project = project();
        ObjectNode slot = capability(project, "WheelSlot", null);
        slot.put("slotNumber", 2);
        addPreset(project, "Red slot", List.of("head"), List.of(slot));

        int colorWheel = play(project).get(BACK).getOrDefault(6, 0);
        assertTrue(colorWheel >= 10 && colorWheel <= 19, "Color wheel at " + colorWheel);
    }

    @Test
    void playsTheProfilesOfTheFixtureLibraryAsTheyAre() throws Exception {
        ObjectNode project = rawProject();
        ObjectNode red = capability(project, "ColorIntensity", 1.0);
        red.put("color", "Red");
        addPreset(project, "Red", List.of("par-1", "head"), List.of(capability(project, "Intensity", 1.0), red));

        Map<String, Map<Integer, Integer>> universes = play(project);

        assertEquals(List.of(255, 255, 0, 0, 0, 5), addresses(universes.get(FRONT), 1, 6));
        assertEquals(255, universes.get(BACK).getOrDefault(5, 0));
    }

    @Test
    void playsAProfileWithASlotOfAWheelItDoesNotHave() throws Exception {
        // E.g. dts/xr4-spot in the fixture library: the channel is named differently than its wheel
        ObjectNode project = project();
        ObjectNode colorWheel = (ObjectNode) project.get("fixtureProfiles").get(1).get("availableChannels").get("Color Wheel");
        ((ObjectNode) project.get("fixtureProfiles").get(1).get("availableChannels")).set("Color Wheel Simple", colorWheel);
        ((ObjectNode) project.get("fixtureProfiles").get(1).get("availableChannels")).remove("Color Wheel");
        ((ObjectNode) project.get("fixtureProfiles").get(1).get("modes").get(0).get("channels").get(5)).put("name", "Color Wheel Simple");
        addPreset(project, "Dimmer", List.of("head"), List.of(capability(project, "Intensity", 1.0)));

        assertEquals(255, play(project).get(BACK).getOrDefault(5, 0));
    }

    @Test
    void readsFractionalDefaultValuesOfTheFixtureLibrary() throws Exception {
        ObjectNode project = project();
        ((ObjectNode) project.get("fixtureProfiles").get(1).get("availableChannels").get("Tilt")).put("defaultValue", "33.3%");

        List<Integer> head = addresses(play(project).get(BACK), 1, 4);

        assertEquals(Math.round(65535 * 0.333), head.get(2) * 256 + head.get(3), 1);
    }


    // A preset lighting the pars in one color only
    private void addColorPreset(ObjectNode project, String name, double red, double blue) {
        ObjectNode redValue = capability(project, "ColorIntensity", red);
        redValue.put("color", "Red");
        ObjectNode greenValue = capability(project, "ColorIntensity", 0.0);
        greenValue.put("color", "Green");
        ObjectNode blueValue = capability(project, "ColorIntensity", blue);
        blueValue.put("color", "Blue");
        addPreset(project, name, List.of("par-1"), List.of(capability(project, "Intensity", 1.0), redValue, greenValue, blueValue));
    }

    private Map<String, Map<Integer, Integer>> preview(ObjectNode project, java.util.function.Consumer<DefaultDesignerService> select) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        Map<String, Map<Integer, Integer>> universes = engine.preview(directory, project.toString(), select, List.of(FRONT, BACK));

        assertEquals(List.of(), engine.errors);
        return universes;
    }

    @Test
    void previewsTheSelectedPreset() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(true);
            designerService.setSelectedPresetUuid("Blue");
            designerService.setSelectedSceneUuids(List.of("Red-scene", "Blue-scene"));
        }).get(FRONT);

        // Dimmer, red, green, blue of par 1
        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }

    @Test
    void previewsTheSelectedScenesWithTheFirstOnTop() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(false);
            designerService.setSelectedPresetUuid("Blue");
            designerService.setSelectedSceneUuids(List.of("Red-scene", "Blue-scene"));
        }).get(FRONT);

        assertEquals(List.of(255, 255, 0, 0), addresses(front, 1, 4));
    }

    @Test
    void previewsTheTimelineOfAComposition() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);

        Map<Integer, Integer> front = preview(project, designerService -> designerService.setPreviewComposition("Song")).get(FRONT);

        assertEquals(List.of(255, 255, 0, 0), addresses(front, 1, 4));
    }

    @Test
    void movesAChannelWithAnEffect() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);

        // A sine on the strobe channel of the pars, at its top 5 seconds into the composition. A project of version 7
        // swings it over half of its amplitude, like the designer did then.
        ObjectNode effect = ((ArrayNode) project.get("presets").get(0).get("effects")).addObject();
        effect.put("type", "curve");
        effect.put("uuid", "strobe-sine");
        effect.put("visible", true);
        effect.put("curveType", "sine");
        effect.putArray("capabilities");
        ObjectNode channels = effect.putArray("channels").addObject();
        channels.put("profileUuid", "test/rgb-par");
        channels.putArray("channels").add("Strobe");
        effect.put("lengthMillis", 20000);
        effect.put("amplitude", 1);
        effect.put("position", 0.5);

        Map<Integer, Integer> front = play(project).get(FRONT);

        assertEquals(191, front.get(6));

        // Since version 8 over its whole amplitude
        project.put("version", 8);
        assertEquals(255, play(project).get(FRONT).get(6));
    }

    @Test
    void setsADimmerSharingItsChannelWithOtherFunctions() throws Exception {
        ObjectNode project = project();
        ObjectNode dimmer = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capabilities": [
                  {"dmxRange": [0, 7], "type": "Intensity", "brightness": "off"},
                  {"dmxRange": [8, 134], "type": "Intensity", "brightnessStart": "dark", "brightnessEnd": "bright"},
                  {"dmxRange": [135, 255], "type": "ShutterStrobe", "shutterEffect": "Strobe"}
                ]}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Dimmer", dimmer);
        addPreset(project, "Half", List.of("par-1"), List.of(capability(project, "Intensity", 0.5)));
        addPreset(project, "Off", List.of("par-2"), List.of(capability(project, "Intensity", 0.0)));

        Map<Integer, Integer> front = play(project).get(FRONT);

        // Half way between dark and bright, not in the strobe range. Off is the start of the range (dark), like in
        // the designer, which also lets the later capability win.
        assertEquals(71, front.get(1));
        assertEquals(8, front.get(11));
    }

}
