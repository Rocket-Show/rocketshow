package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.play.CompositionPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The DMX values the designer engine sends for the fixtures of a project: the channels of a preset, fine channels,
 * default values, color wheels and several fixtures and universes.
 */
class DesignerDmxTest {

    private static final String FRONT = "universe-front";
    private static final String BACK = "universe-back";

    // An RGB par with an unused channel, a moving head with 16 bit pan and tilt and a color wheel and an RGB par without
    // a dimmer (like the ADJ Dotz Par in its 3-channel mode). Par 1 starts at DMX address 1 of the front universe, par 2
    // at address 11, the par without dimmer at address 21, the moving head at address 1 of the back universe.
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
                },
                {
                  "uuid": "test/rgb",
                  "name": "RGB par without dimmer",
                  "availableChannels": {
                    "Red": {"capability": {"type": "ColorIntensity", "color": "Red"}},
                    "Green": {"capability": {"type": "ColorIntensity", "color": "Green"}},
                    "Blue": {"capability": {"type": "ColorIntensity", "color": "Blue"}}
                  },
                  "modes": [{"name": "3-channel", "shortName": "3ch", "channels": ["Red", "Green", "Blue"]}]
                }
              ],
              "fixtures": [
                {"uuid": "par-1", "name": "Par 1", "profileUuid": "test/rgb-par", "modeShortName": "6ch", "dmxUniverseUuid": "universe-front", "dmxFirstChannel": 0},
                {"uuid": "par-2", "name": "Par 2", "profileUuid": "test/rgb-par", "modeShortName": "6ch", "dmxUniverseUuid": "universe-front", "dmxFirstChannel": 10},
                {"uuid": "head", "name": "Head", "profileUuid": "test/moving-head", "modeShortName": "6ch", "dmxUniverseUuid": "universe-back", "dmxFirstChannel": 0},
                {"uuid": "rgb", "name": "RGB", "profileUuid": "test/rgb", "modeShortName": "3ch", "dmxUniverseUuid": "universe-front", "dmxFirstChannel": 20}
              ],
              "presetFixtures": [{"fixtureUuid": "par-1"}, {"fixtureUuid": "par-2"}, {"fixtureUuid": "head"}, {"fixtureUuid": "rgb"}],
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
    void dimsTheBrightnessWithTheMasterDimmer() throws Exception {
        ObjectNode project = project();
        project.put("masterDimmerValue", 0.5);
        ObjectNode red = capability(project, "ColorIntensity", 1.0);
        red.put("color", "Red");
        addPreset(project, "Red", List.of("par-1"), List.of(capability(project, "Intensity", 1.0), red));

        // Half the brightness, the color stays like in the preview of the designer
        assertEquals(List.of(127, 255), addresses(play(project).get(FRONT), 1, 2));

        // A dimmer sharing its channel with other functions is dimmed within its range (8 to 134), not into the strobe
        ObjectNode dimmer = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capabilities": [
                  {"dmxRange": [0, 7], "type": "Intensity", "brightness": "off"},
                  {"dmxRange": [8, 134], "type": "Intensity", "brightnessStart": "dark", "brightnessEnd": "bright"},
                  {"dmxRange": [135, 255], "type": "ShutterStrobe", "shutterEffect": "Strobe"}
                ]}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Dimmer", dimmer);
        // Half of the brightness: half way from dark (1 %) to bright
        assertEquals(70, play(project).get(FRONT).get(1));
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

    // The dimmer of the pars: off until 7, from dark to bright between 8 and 134, then the strobe
    private void shareTheDimmersOfThePars(ObjectNode project) {
        ObjectNode dimmer = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capabilities": [
                  {"dmxRange": [0, 7], "type": "Intensity", "brightness": "off"},
                  {"dmxRange": [8, 134], "type": "Intensity", "brightnessStart": "dark", "brightnessEnd": "bright"},
                  {"dmxRange": [135, 255], "type": "ShutterStrobe", "shutterEffect": "Strobe"}
                ]}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Dimmer", dimmer);
    }

    // A preset setting a channel of the passed fixture to a DMX value
    private void addChannelPreset(ObjectNode project, String name, String fixtureUuid, String profileUuid, String channelName, double value) {
        addPreset(project, name, List.of(fixtureUuid), List.of());
        ArrayNode presets = (ArrayNode) project.get("presets");
        ((ArrayNode) presets.get(presets.size() - 1).get("steps").get(0).get("fixtureChannelValues")).addObject()
                .put("channelName", channelName)
                .put("profileUuid", profileUuid)
                .put("value", value);
    }

    @Test
    void sendsTheValuesAsTheyAreWithTheMasterDimmerAtFull() throws Exception {
        // Dimmed by a factor of 1 within its range, 43 would come out a hair below (42.99999999999999), which the DMX
        // value rounds down
        ObjectNode project = project();
        shareTheDimmersOfThePars(project);
        addChannelPreset(project, "Dimmer", "par-1", "test/rgb-par", "Dimmer", 43);

        assertEquals(43, play(project).get(FRONT).get(1));
    }

    @Test
    void dimsADimmerWithinTheRangeItIsIn() throws Exception {
        ObjectNode project = project();
        project.put("masterDimmerValue", 0.5);
        shareTheDimmersOfThePars(project);
        addChannelPreset(project, "Bright", "par-1", "test/rgb-par", "Dimmer", 134);
        addChannelPreset(project, "Off", "par-2", "test/rgb-par", "Dimmer", 3);

        Map<Integer, Integer> front = play(project).get(FRONT);

        // Bright, right on the end of its range, half way from dark (1 %) to bright. Off is a single brightness, which
        // can't be dimmed.
        assertEquals(70, front.get(1));
        assertEquals(3, front.get(11));
    }

    @Test
    void dimsAnInvertedDimmerFromItsBrightEnd() throws Exception {
        ObjectNode project = project();
        project.put("masterDimmerValue", 0.5);
        ObjectNode dimmer = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capability": {"type": "Intensity", "brightnessStart": "bright", "brightnessEnd": "off"}}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Dimmer", dimmer);
        addChannelPreset(project, "Bright", "par-1", "test/rgb-par", "Dimmer", 0);

        // Bright at 0, the start of the channel: half of it is half way to off
        assertEquals(127, play(project).get(FRONT).get(1));
    }

    @Test
    void sendsEachByteOfA24BitChannel() throws Exception {
        ObjectNode project = project();
        ObjectNode head = (ObjectNode) project.get("fixtureProfiles").get(1);
        ((ObjectNode) head.get("availableChannels").get("Pan")).putArray("fineChannelAliases").add("Pan fine").add("Pan finer");
        ObjectNode finer = ((ArrayNode) head.get("modes").get(0).get("channels")).insertObject(2);
        finer.put("name", "Pan finer");
        finer.putArray("repeatFor");
        finer.putArray("templateChannels");
        addChannelPreset(project, "Pan", "head", "test/moving-head", "Pan", 0x123456);

        assertEquals(List.of(0x12, 0x34, 0x56), addresses(play(project).get(BACK), 1, 3));
    }

    // A bar of three pixels in two modes, as the designer saves it: its red, green and blue channels per channel (all
    // reds, then all greens and all blues) from address 31, and a 16 bit dimmer per pixel from address 41
    private void addPixelBars(ObjectNode project) {
        ObjectNode bar = (ObjectNode) objectMapper.readTree("""
                {
                  "uuid": "test/pixel-bar",
                  "name": "Pixel bar",
                  "matrix": {"pixelCount": [3, 1, 1]},
                  "availableChannels": {},
                  "templateChannels": {
                    "Red $pixelKey": {"fineChannelAliases": [], "capability": {"type": "ColorIntensity", "color": "Red"}},
                    "Green $pixelKey": {"fineChannelAliases": [], "capability": {"type": "ColorIntensity", "color": "Green"}},
                    "Blue $pixelKey": {"fineChannelAliases": [], "capability": {"type": "ColorIntensity", "color": "Blue"}},
                    "Dimmer $pixelKey": {"fineChannelAliases": ["Dimmer $pixelKey fine"], "capability": {"type": "Intensity"}}
                  },
                  "modes": [
                    {"name": "9-channel", "shortName": "9ch", "channels": [{"name": "", "insert": "matrixChannels", "repeatFor": ["eachPixelABC"],
                      "channelOrder": "perChannel", "templateChannels": ["Red $pixelKey", "Green $pixelKey", "Blue $pixelKey"]}]},
                    {"name": "6-channel", "shortName": "6ch", "channels": [{"name": "", "insert": "matrixChannels", "repeatFor": ["eachPixelABC"],
                      "channelOrder": "perPixel", "templateChannels": ["Dimmer $pixelKey", "Dimmer $pixelKey fine"]}]}
                  ]
                }
                """);
        ((ArrayNode) project.get("fixtureProfiles")).add(bar);

        for (String mode : List.of("9ch", "6ch")) {
            ObjectNode fixture = ((ArrayNode) project.get("fixtures")).addObject();
            fixture.put("uuid", "bar-" + mode);
            fixture.put("name", "Bar " + mode);
            fixture.put("profileUuid", "test/pixel-bar");
            fixture.put("modeShortName", mode);
            fixture.put("dmxUniverseUuid", FRONT);
            fixture.put("dmxFirstChannel", mode.equals("9ch") ? 30 : 40);

            for (String pixelKey : List.of("1", "2", "3")) {
                ((ArrayNode) project.get("presetFixtures")).addObject().put("fixtureUuid", "bar-" + mode).put("pixelKey", pixelKey);
            }
        }
    }

    // A preset for one pixel of a bar
    private void addPixelPreset(ObjectNode project, String name, String fixtureUuid, String pixelKey, List<ObjectNode> capabilityValues) {
        addPreset(project, name, List.of(fixtureUuid), capabilityValues);
        ArrayNode presets = (ArrayNode) project.get("presets");
        ((ObjectNode) presets.get(presets.size() - 1).get("fixtures").get(0)).put("pixelKey", pixelKey);
    }

    @Test
    void sendsThePixelsOfABarChannelByChannel() throws Exception {
        ObjectNode project = project();
        addPixelBars(project);
        addPixelPreset(project, "Red", "bar-9ch", "2", List.of(color(project, "Red", 1.0)));
        addPixelPreset(project, "Blue", "bar-9ch", "3", List.of(color(project, "Blue", 1.0)));

        // Red 1 to 3, green 1 to 3, blue 1 to 3
        assertEquals(List.of(0, 255, 0, 0, 0, 0, 0, 0, 255), addresses(play(project).get(FRONT), 31, 39));
    }

    @Test
    void sendsTheFineChannelsOfThePixels() throws Exception {
        ObjectNode project = project();
        addPixelBars(project);
        addPixelPreset(project, "Half", "bar-6ch", "1", List.of(capability(project, "Intensity", 0.5)));
        addPixelPreset(project, "Full", "bar-6ch", "2", List.of(capability(project, "Intensity", 1.0)));

        // Dimmer 1 and its fine channel, then the ones of the pixels 2 and 3
        assertEquals(List.of(127, 255, 255, 255, 0, 0), addresses(play(project).get(FRONT), 41, 46));
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
    void strobesFromItsPositionAndOpensTheShutterAtZero() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Strobe", List.of("par-1"), List.of(capability(project, "ShutterStrobe", 0.5)));
        addPreset(project, "Open", List.of("par-2"), List.of(capability(project, "ShutterStrobe", 0.0)));

        Map<Integer, Integer> front = play(project).get(FRONT);

        // Half way between the slow and the fast end of the strobe (10 to 255), the shutter of par 2 open (0 to 9)
        assertEquals(132, front.get(6));
        assertEquals(4, front.get(16));
    }

    @Test
    void showsTheClosestColorMacroAndSwitchesTheHazeOff() throws Exception {
        ObjectNode project = project();
        ArrayNode profiles = (ArrayNode) project.get("fixtureProfiles");
        profiles.add(objectMapper.readTree("""
                {
                  "uuid": "test/macros",
                  "name": "Par with color macros",
                  "availableChannels": {
                    "Dimmer": {"capability": {"type": "Intensity"}, "fineChannelAliases": []},
                    "Macro": {"fineChannelAliases": [], "capabilities": [
                      {"dmxRange": [0, 9], "type": "NoFunction"},
                      {"dmxRange": [10, 49], "type": "ColorPreset", "colors": ["#ff0000"]},
                      {"dmxRange": [50, 89], "type": "ColorPreset", "colors": ["#0000ff"]}
                    ]},
                    "Haze": {"fineChannelAliases": [], "capabilities": [
                      {"dmxRange": [0, 9], "type": "NoFunction"},
                      {"dmxRange": [10, 255], "type": "Fog", "fogType": "Haze", "fogOutputStart": "weak", "fogOutputEnd": "strong"}
                    ]}
                  },
                  "modes": [{"name": "3-channel", "shortName": "3ch", "channels": [{"name": "Dimmer"}, {"name": "Macro"}, {"name": "Haze"}]}]
                }
                """));
        ((ArrayNode) project.get("fixtures")).addObject()
                .put("uuid", "macros")
                .put("name", "Macros")
                .put("profileUuid", "test/macros")
                .put("modeShortName", "3ch")
                .put("dmxUniverseUuid", FRONT)
                .put("dmxFirstChannel", 30);
        ((ArrayNode) project.get("presetFixtures")).addObject().put("fixtureUuid", "macros");

        ObjectNode red = capability(project, "ColorIntensity", 0.9);
        red.put("color", "Red");
        ObjectNode green = capability(project, "ColorIntensity", 0.1);
        green.put("color", "Green");
        ObjectNode blue = capability(project, "ColorIntensity", 0.2);
        blue.put("color", "Blue");
        addPreset(project, "Red", List.of("macros"), List.of(capability(project, "Intensity", 1.0), red, green, blue, capability(project, "FogOutput", 0.0)));

        Map<Integer, Integer> front = play(project).get(FRONT);

        // full, the red macro in its middle, the haze off
        assertEquals(List.of(255, 29, 4), addresses(front, 31, 33));
    }

    @Test
    void identifiesAFixtureFromTheFixturePoolOfTheDesigner() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        ((ArrayNode) project.get("presets").get(0).get("fixtures")).addObject().put("fixtureUuid", "par-2");

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(true);
            designerService.setSelectedPresetUuid("Red");
            designerService.setIdentifyFixtureUuids(List.of("par-2", "rgb"));
        }).get(FRONT);

        // Par 1 plays the preset, par 2 is lit at full white with its shutter open, like the par without a dimmer
        assertEquals(List.of(255, 255, 0, 0, 0, 5), addresses(front, 1, 6));
        assertEquals(List.of(255, 255, 255, 255, 0, 4), addresses(front, 11, 16));
        assertEquals(List.of(255, 255, 255), addresses(front, 21, 23));
    }

    @Test
    void identifiesTheFixturesTheDesignerSendsWithItsPreview() throws Exception {
        ObjectNode project = project();
        project.putArray("identifyFixtureUuids").add("par-1");

        // like DesignerController.preview, which reads them from the project the designer sends
        Map<Integer, Integer> front = new DesignerTestEngine().start(directory, project.toString(), (designerService, loadedProject) -> {
            designerService.stopPreview();
            designerService.load(null, loadedProject, null);
            designerService.setIdentifyFixtureUuids(loadedProject.getIdentifyFixtureUuids());
            designerService.startPreview(0);
        }, List.of(FRONT, BACK)).get(FRONT);

        assertEquals(List.of(255, 255, 255, 255, 0, 4), addresses(front, 1, 6));
        assertEquals(List.of(0, 0, 0, 0, 0, 5), addresses(front, 11, 16));
    }

    @Test
    void forgetsTheIdentifiedFixturesWithTheNextProject() throws Exception {
        ObjectNode project = project();

        Map<Integer, Integer> front = new DesignerTestEngine().start(directory, project.toString(), (designerService, loadedProject) -> {
            designerService.setIdentifyFixtureUuids(List.of("par-2"));
            designerService.load(null, loadedProject, null);
            designerService.startPreview(0);
        }, List.of(FRONT, BACK)).get(FRONT);

        assertEquals(List.of(0, 0, 0, 0, 0, 5), addresses(front, 11, 16));
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

        // Half of the brightness: half way from dark (1 %) to bright, not in the strobe range. Off is the capability
        // giving off.
        assertEquals(70, front.get(1));
        assertEquals(3, front.get(11));
    }

    @Test
    void setsTheBrightnessGivenInLumens() throws Exception {
        ObjectNode project = project();
        ObjectNode dimmer = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capabilities": [
                  {"dmxRange": [0, 127], "type": "Intensity", "brightnessStart": "0lm", "brightnessEnd": "500lm"},
                  {"dmxRange": [128, 255], "type": "Intensity", "brightnessStart": "500lm", "brightnessEnd": "1000lm"}
                ]}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Dimmer", dimmer);
        addPreset(project, "Quarter", List.of("par-1"), List.of(capability(project, "Intensity", 0.25)));
        addPreset(project, "Three quarters", List.of("par-2"), List.of(capability(project, "Intensity", 0.75)));

        Map<Integer, Integer> front = play(project).get(FRONT);

        // Compared to the brightest capability (1000 lm)
        assertEquals(63, front.get(1));
        assertEquals(191, front.get(11));
    }

    private static ObjectNode color(ObjectNode values, String color, double valuePercentage) {
        ObjectNode capability = capability(values, "ColorIntensity", valuePercentage);
        capability.put("color", color);
        return capability;
    }

    // Hold the scene of the passed preset at the passed dimmer
    private static void dimScene(ObjectNode project, String presetName, double dimmer) {
        for (var scene : project.get("scenes")) {
            if (scene.get("uuid").asString().equals(presetName + "-scene")) {
                ((ObjectNode) scene).put("dimmer", dimmer);
            }
        }
    }

    @Test
    void lightsAFixtureWithoutDimmerInItsColorAlone() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Red", List.of("rgb"), List.of(color(project, "Red", 1.0)));

        assertEquals(List.of(255, 0, 0), addresses(play(project).get(FRONT), 21, 23));
    }

    @Test
    void dimsAFixtureWithoutDimmerWithAVirtualOne() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Red", List.of("rgb"), List.of(capability(project, "Intensity", 0.5), color(project, "Red", 1.0), color(project, "Blue", 0.4)));

        // Half of red and of 40 % blue
        assertEquals(List.of(127, 0, 51), addresses(play(project).get(FRONT), 21, 23));
    }

    @Test
    void dimsAFixtureWithoutDimmerWithTheMasterDimmer() throws Exception {
        ObjectNode project = project();
        project.put("masterDimmerValue", 0.5);
        addPreset(project, "Red", List.of("rgb"), List.of(color(project, "Red", 1.0)));

        assertEquals(127, play(project).get(FRONT).get(21));
    }

    @Test
    void dimsAFixtureWithoutDimmerWithACurveOnTheDimmer() throws Exception {
        ObjectNode project = project();
        project.put("version", 8);
        addPreset(project, "Red", List.of("rgb"), List.of(color(project, "Red", 1.0)));

        // A sine on the dimmer, back in the middle of its swing 5 seconds into the composition
        ObjectNode effect = ((ArrayNode) project.get("presets").get(0).get("effects")).addObject();
        effect.put("type", "curve");
        effect.put("uuid", "dimmer-sine");
        effect.put("visible", true);
        effect.put("curveType", "sine");
        effect.putArray("capabilities").addObject().put("type", "Intensity");
        effect.putArray("channels");
        effect.put("lengthMillis", 10000);
        effect.put("amplitude", 1);
        effect.put("position", 0.5);

        assertEquals(127, play(project).get(FRONT).get(21));
    }

    @Test
    void fadesAFixtureWithADimmerInItsDimmerOnly() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Red", List.of("par-1"), List.of(capability(project, "Intensity", 1.0), color(project, "Red", 1.0)));
        dimScene(project, "Red", 0.3);

        // The dimmer at 30 %, the color stays: the light is at 30 %, not at 30 % of 30 %
        assertEquals(List.of(76, 255, 0, 0), addresses(play(project).get(FRONT), 1, 4));
    }

    @Test
    void fadesAFixtureWithoutDimmerOnce() throws Exception {
        ObjectNode project = project();
        addPreset(project, "Red", List.of("rgb"), List.of(capability(project, "Intensity", 1.0), color(project, "Red", 1.0)));
        dimScene(project, "Red", 0.3);

        assertEquals(76, play(project).get(FRONT).get(21));
    }

    @Test
    void blendsTheColorsOfAFadingPresetWithTheOnesBelow() throws Exception {
        ObjectNode project = project();
        // the first scene is on top
        addColorPreset(project, "Blue", 0, 1);
        addColorPreset(project, "Red", 1, 0);
        dimScene(project, "Blue", 0.5);

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(false);
            designerService.setSelectedSceneUuids(List.of("Blue-scene", "Red-scene"));
        }).get(FRONT);

        // Both light at full: half red, half blue, the dimmer stays at full
        assertEquals(List.of(255, 127, 0, 127), addresses(front, 1, 4));
    }

    @Test
    void keepsTheColorOfAPresetFadingToDark() throws Exception {
        ObjectNode project = project();
        // the first scene is on top
        addPreset(project, "Dark", List.of("par-1"), List.of(capability(project, "Intensity", 0.0), color(project, "Blue", 1.0)));
        addColorPreset(project, "Red", 1, 0);
        dimScene(project, "Dark", 0.5);

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(false);
            designerService.setSelectedSceneUuids(List.of("Dark-scene", "Red-scene"));
        }).get(FRONT);

        // Half way to dark, still in red
        assertEquals(List.of(127, 255, 0, 0), addresses(front, 1, 4));
    }

    // A red preset for the passed fixtures with a square on their dimmers, chasing over them: each one is half a period
    // after the one before it, so neighbours in the chase are on and off in turns. 5 seconds into the composition, the
    // first step of the chase is off and the second one on.
    private ObjectNode addDimmerChase(ObjectNode project, List<String> fixtureUuids) {
        project.put("version", 8);
        addPreset(project, "Chase", fixtureUuids, List.of(color(project, "Red", 1.0)));

        ObjectNode preset = (ObjectNode) project.get("presets").get(0);
        ObjectNode effect = ((ArrayNode) preset.get("effects")).addObject();
        effect.put("type", "curve");
        effect.put("uuid", "dimmer-chase");
        effect.put("visible", true);
        effect.put("curveType", "square");
        effect.putArray("capabilities").addObject().put("type", "Intensity");
        effect.putArray("channels");
        effect.put("lengthMillis", 2000);
        effect.put("phasingMillis", 1000);
        effect.put("amplitude", 1);
        effect.put("position", 0.5);

        return preset;
    }

    // The dimmers of par 1 and par 2
    private static List<Integer> parDimmers(Map<Integer, Integer> front) {
        return List.of(front.getOrDefault(1, 0), front.getOrDefault(11, 0));
    }

    @Test
    void chasesInTheFixtureOrderOfTheProject() throws Exception {
        ObjectNode project = project();
        addDimmerChase(project, List.of("par-2", "par-1"));

        // Par 1 comes first in the project, whatever the order of the preset
        assertEquals(List.of(0, 255), parDimmers(play(project).get(FRONT)));
    }

    @Test
    void chasesInTheFixtureOrderOfThePreset() throws Exception {
        ObjectNode project = project();
        ObjectNode preset = addDimmerChase(project, List.of("par-2", "par-1"));
        preset.put("useGlobalFixtureOrder", false);

        assertEquals(List.of(255, 0), parDimmers(play(project).get(FRONT)));
    }

    @Test
    void chasesFixturesOnTheSameAddressInOneStep() throws Exception {
        ObjectNode project = project();

        // A second par on the address of par 1, right after it in the order of the project: the same lamp
        ObjectNode twin = ((ArrayNode) project.get("fixtures")).addObject();
        twin.put("uuid", "par-1-twin");
        twin.put("name", "Par 1 twin");
        twin.put("profileUuid", "test/rgb-par");
        twin.put("modeShortName", "6ch");
        twin.put("dmxUniverseUuid", FRONT);
        twin.put("dmxFirstChannel", 0);
        ((ArrayNode) project.get("presetFixtures")).insertObject(1).put("fixtureUuid", "par-1-twin");

        addDimmerChase(project, List.of("par-1", "par-1-twin", "par-2"));

        // Par 2 is the second step of the chase, not the third one
        assertEquals(List.of(0, 255), parDimmers(play(project).get(FRONT)));
    }

    // Start the engine the way the passed function does with the project, e.g. playing the scenes picked by hand on
    // the Play screen, and return the first DMX values it sends
    private Map<String, Map<Integer, Integer>> start(ObjectNode project, java.util.function.BiConsumer<DefaultDesignerService, Project> start) throws Exception {
        DesignerTestEngine engine = new DesignerTestEngine();
        Map<String, Map<Integer, Integer>> universes = engine.start(directory, project.toString(), start, List.of(FRONT, BACK));

        assertEquals(List.of(), engine.errors);
        return universes;
    }

    // A composition playing the project, the default composition (played in between the others) or a regular one
    private static CompositionPlayer compositionPlayer(boolean defaultComposition) {
        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        composition.setName("Song");
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);
        when(compositionPlayer.isDefaultComposition()).thenReturn(defaultComposition);
        return compositionPlayer;
    }

    @Test
    void playsTheScenesPickedByHandOnThePlayScreen() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = start(project, (designerService, loadedProject) ->
                designerService.playManualScenes(loadedProject, List.of("Blue-scene"), 1)).get(FRONT);

        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }

    @Test
    void dimsTheScenesPickedByHandWithTheMasterDimmerOfThePlayScreen() throws Exception {
        ObjectNode project = project();
        project.put("masterDimmerValue", 0.5);
        addColorPreset(project, "Red", 1, 0);

        // On top of the master dimmer of the project: a quarter of the brightness, the color stays
        Map<Integer, Integer> front = start(project, (designerService, loadedProject) ->
                designerService.playManualScenes(loadedProject, List.of("Red-scene"), 0.5)).get(FRONT);
        assertEquals(List.of(63, 255), addresses(front, 1, 2));

        // A blackout turns the master dimmer of the Play screen all the way down
        front = start(project, (designerService, loadedProject) ->
                designerService.playManualScenes(loadedProject, List.of("Red-scene"), 0)).get(FRONT);
        assertEquals(List.of(0, 255), addresses(front, 1, 2));
    }

    @Test
    void bringsTheScenesPickedByHandBackOnceTheProjectOfACompositionCloses() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = start(project, (designerService, loadedProject) -> {
            // The composition lights the stage with its own project: the scenes wait
            designerService.load(compositionPlayer(false), loadedProject, null);
            designerService.playManualScenes(loadedProject, List.of("Blue-scene"), 1);
            assertTrue(designerService.hasManualScenes());
            assertFalse(designerService.isPlayingManualScenes());

            // ... until it stops
            designerService.close();
            assertTrue(designerService.isPlayingManualScenes());
        }).get(FRONT);

        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }

    @Test
    void playsTheScenesPickedByHandInsteadOfTheLightsOfTheDefaultComposition() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = start(project, (designerService, loadedProject) -> {
            designerService.load(compositionPlayer(true), loadedProject, null);
            designerService.playManualScenes(loadedProject, List.of("Blue-scene"), 1);
            assertTrue(designerService.isPlayingManualScenes());
        }).get(FRONT);

        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }

    @Test
    void keepsPlayingTheScenesPickedByHandAlongWithACompositionWithoutProject() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = start(project, (designerService, loadedProject) -> {
            designerService.playManualScenes(loadedProject, List.of("Blue-scene"), 1);

            // A composition without a project closes the one played so far when it loads, pauses the engine and
            // starts it again. The live preview of the designer is turned off in the settings.
            designerService.close();
            designerService.pause();
            designerService.play();
            designerService.stopPreview();

            assertTrue(designerService.isPlayingManualScenes());
        }).get(FRONT);

        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }

    @Test
    void bringsBackTheScenesPickedLast() throws Exception {
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        addColorPreset(project, "Blue", 0, 1);

        Map<Integer, Integer> front = start(project, (designerService, loadedProject) -> {
            designerService.load(compositionPlayer(false), loadedProject, null);
            designerService.playManualScenes(loadedProject, List.of("Red-scene"), 1);

            // Released while the composition plays: nothing would come back
            designerService.stopManualScenes();
            assertFalse(designerService.hasManualScenes());

            designerService.playManualScenes(loadedProject, List.of("Blue-scene"), 1);
            designerService.close();
        }).get(FRONT);

        assertEquals(List.of(255, 0, 0, 255), addresses(front, 1, 4));
    }


    // ---- read and played like the designer previews it ----

    @Test
    void keepsTheDefaultsOfTheDesignerForValuesWhichAreNull() throws Exception {
        // The designer writes null for a number it couldn't work out, and its own preview takes the default instead
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        project.putNull("masterDimmerValue");
        ObjectNode scene = (ObjectNode) project.get("scenes").get(0);
        scene.putNull("dimmer");
        scene.putNull("fadeInMillis");
        scene.putNull("fadeOutMillis");

        assertEquals(List.of(255, 255, 0, 0), addresses(play(project).get(FRONT), 1, 4));
    }

    @Test
    void fadesNoSceneWithoutFadeTimes() throws Exception {
        // A scene of the designer starts without fades, so one not giving them doesn't fade (it faded in over two
        // seconds here before): 100 ms into its clip, it's at full already
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        ObjectNode scene = (ObjectNode) project.get("scenes").get(0);
        scene.remove("fadeInMillis");
        scene.remove("fadeOutMillis");
        ((ObjectNode) project.get("compositions").get(0).get("scenePlaybackRegions").get(0)).put("startMillis", 4900);

        assertEquals(List.of(255, 255, 0, 0), addresses(play(project).get(FRONT), 1, 4));
    }

    @Test
    void playsAProjectWithACapabilityOfANewerFixtureLibrary() throws Exception {
        // The designer doesn't know the type either and leaves the capability out, the rest of the project plays
        ObjectNode project = project();
        ObjectNode laser = (ObjectNode) objectMapper.readTree("""
                {"fineChannelAliases": [], "capability": {"type": "SomethingNew", "color": "Ultraviolet"}}
                """);
        ((ObjectNode) project.get("fixtureProfiles").get(0).get("availableChannels")).set("Laser", laser);
        addColorPreset(project, "Red", 1, 0);

        assertEquals(List.of(255, 255, 0, 0), addresses(play(project).get(FRONT), 1, 4));
    }

    @Test
    void previewsNothingForAPresetDeletedInTheDesigner() throws Exception {
        // The designer still names the preset it deleted last: its preview shows nothing, and so does the engine
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);

        Map<Integer, Integer> front = preview(project, designerService -> {
            designerService.setPreviewPreset(true);
            designerService.setSelectedPresetUuid("deleted");
        }).get(FRONT);

        // only the default value of the strobe
        assertEquals(List.of(0, 0, 0, 0, 0, 5), addresses(front, 1, 6));
    }

    @Test
    void lightsAFixtureNamedWithAnEmptyPixelKey() throws Exception {
        // An empty pixel key is no pixel key, like in the designer
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        ((ObjectNode) project.get("presets").get(0).get("fixtures").get(0)).put("pixelKey", "");

        assertEquals(List.of(255, 255, 0, 0), addresses(play(project).get(FRONT), 1, 4));
    }

    @Test
    void leavesTheWheelAloneForAColorWithoutValue() throws Exception {
        // The designer approximates no slot from a color it has no value for
        ObjectNode project = project();
        ObjectNode red = capability(project, "ColorIntensity", null);
        red.put("color", "Red");
        ObjectNode green = capability(project, "ColorIntensity", 0.0);
        green.put("color", "Green");
        ObjectNode blue = capability(project, "ColorIntensity", 1.0);
        blue.put("color", "Blue");
        addPreset(project, "Blue", List.of("head"), List.of(capability(project, "Intensity", 1.0), red, green, blue));

        Map<Integer, Integer> back = play(project).get(BACK);

        assertEquals(255, back.get(5));
        assertEquals(0, back.getOrDefault(6, 0));
    }

    @Test
    void playsTheFixturesOfAPresetOfVersion1() throws Exception {
        // Before version 2, a preset (and the project) only named its fixtures
        ObjectNode project = project();
        addColorPreset(project, "Red", 1, 0);
        project.put("version", 1);
        project.remove("presetFixtures");
        ObjectNode preset = (ObjectNode) project.get("presets").get(0);
        preset.remove("fixtures");
        preset.putArray("fixtureUuids").add("par-1").add("par-2");
        // the values are kept on the preset itself then, there are no steps yet
        preset.set("fixtureCapabilityValues", preset.get("steps").get(0).get("fixtureCapabilityValues"));
        preset.remove("steps");

        Map<Integer, Integer> front = play(project).get(FRONT);

        assertEquals(List.of(255, 255, 0, 0), addresses(front, 1, 4));
        assertEquals(List.of(255, 255, 0, 0), addresses(front, 11, 14));
    }

    // A square on the strobe channel of the pars, one beat long. It's chased by a fixed time of half a beat at 120 BPM,
    // which lights par 2 at 120 BPM, while it's dark at 60 BPM (where it is half way through its longer beat).
    private static void addBeatSyncedStrobe(ObjectNode project) {
        ObjectNode effect = ((ArrayNode) project.get("presets").get(0).get("effects")).addObject();
        effect.put("type", "curve");
        effect.put("uuid", "strobe-square");
        effect.put("curveType", "square");
        effect.putArray("capabilities");
        ObjectNode channels = effect.putArray("channels").addObject();
        channels.put("profileUuid", "test/rgb-par");
        channels.putArray("channels").add("Strobe");
        effect.put("lengthMode", "beats");
        effect.put("lengthBeats", 4);
        effect.put("phasingMode", "millis");
        effect.put("phasingMillis", 2000);
    }

    @Test
    void runsTheCurvesOnTheTempoOfTheCompositionOpenInTheDesigner() throws Exception {
        // The preview of a preset follows the tempo of the composition open in the designer, even while it isn't played
        ObjectNode project = project();
        addPreset(project, "Strobe", List.of("par-1", "par-2"), List.of());
        addBeatSyncedStrobe(project);
        ((ObjectNode) project.get("compositions").get(0)).put("beatsPerMinute", 60);

        java.util.function.Consumer<DefaultDesignerService> previewPreset = designerService -> {
            designerService.setPreviewPreset(true);
            designerService.setSelectedPresetUuid("Strobe");
        };

        // no composition open: the default tempo of 120 BPM
        assertEquals(255, preview(project, previewPreset).get(FRONT).getOrDefault(16, 0));

        project.put("selectedCompositionUuid", "song");
        assertEquals(0, preview(project, previewPreset).get(FRONT).getOrDefault(16, 0));
    }

}
