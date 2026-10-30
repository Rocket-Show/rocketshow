package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The conformance cases of the designer (web/projects/designer/conformance of the designer, copied to
 * src/test/resources/designer-conformance, see its README): the engine has to come to the very same values as the
 * preview of the designer, which recorded the expected values. Each kind of case runs what the designer runs for it
 * (conformance.spec.ts of the designer), the numbers are compared exactly.
 */
class DesignerConformanceTest {

    private static final Path CONFORMANCE = Path.of("src/test/resources/designer-conformance");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @TempDir
    Path directory;

    @TestFactory
    Stream<DynamicContainer> comesToTheValuesOfTheDesigner() throws IOException {
        List<DynamicContainer> files = new ArrayList<>();

        try (Stream<Path> paths = Files.list(CONFORMANCE.resolve("cases"))) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonNode caseFile = DesignerJson.MAPPER.readTree(path.toFile());
                String kind = caseFile.get("kind").asString();
                List<DynamicTest> cases = new ArrayList<>();

                for (JsonNode conformanceCase : caseFile.get("cases")) {
                    cases.add(DynamicTest.dynamicTest(conformanceCase.get("name").asString(), () -> {
                        assertNotNull(conformanceCase.get("expected"), "Not recorded yet, see the README of the cases");
                        assertNull(difference(conformanceCase.get("expected"), run(kind, conformanceCase.get("input")), ""));
                    }));
                }

                files.add(DynamicContainer.dynamicContainer(path.getFileName().toString(), cases));
            }
        }

        return files.stream();
    }

    // ---- the kinds of cases ----

    private JsonNode run(String kind, JsonNode input) throws Exception {
        return switch (kind) {
            case "transitionCurve" -> number(TransitionCurve.apply(text(input.get("curve")), input.get("position").isNull() ? Double.NaN : input.get("position").asDouble()));
            case "effectCurve" -> effectCurve(input);
            case "presetSteps" -> presetSteps(input);
            case "presetIntensity" -> presetIntensity(input);
            case "profileNumber" -> number(DefaultDesignerService.parseNumber(input.get("text").asString()));
            case "defaultValue" -> number(DefaultDesignerService.getDefaultValueByChannel(DesignerJson.MAPPER.treeToValue(input.get("channel"), FixtureChannel.class)));
            case "hexColor" -> color(DefaultDesignerService.hexToRgb(input.get("text").asString()));
            case "mixColors" -> color(DefaultDesignerService.mixHexColors(strings(input.get("colors"))));
            case "pixels" -> pixels(input);
            case "pixelGroup" -> pixelGroup(input);
            case "channelValues" -> channelValues(input);
            case "migration" -> migration(input);
            default -> throw new IllegalArgumentException("Unknown kind " + kind);
        };
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static Integer integer(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static Double decimal(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }

    private static List<String> strings(JsonNode node) {
        List<String> strings = new ArrayList<>();
        node.forEach(item -> strings.add(item.asString()));
        return strings;
    }

    private static JsonNode number(Double value) {
        return value == null ? NODES.nullNode() : NODES.numberNode(value);
    }

    private static JsonNode color(Color color) {
        return color == null ? NODES.nullNode() : NODES.arrayNode().add(color.getRed()).add(color.getGreen()).add(color.getBlue());
    }

    private JsonNode effectCurve(JsonNode input) {
        EffectCurve curve = (EffectCurve) DesignerJson.MAPPER.treeToValue(input.get("curve"), Effect.class);
        Double bpm = decimal(input.get("beatsPerMinute"));
        Integer fixtureCount = integer(input.get("fixtureCount"));

        ObjectNode result = NODES.objectNode();
        result.put("lengthMillis", curve.getLengthMillis(bpm));
        result.put("phaseMillis", curve.getPhaseMillis(bpm));
        result.set("runEndMillis", number(curve.getRunEndMillis(bpm)));
        result.set("runLoopMillis", number(curve.getRunLoopMillis(fixtureCount, bpm)));

        ArrayNode samples = result.putArray("samples");

        for (JsonNode sample : input.get("samples")) {
            Integer fixtureIndex = integer(sample.get("fixtureIndex"));
            samples.addArray()
                    .add(curve.getPhasingMillis(fixtureIndex, fixtureCount, bpm))
                    .add(number(curve.getValueAtMillis(sample.get("timeMillis").asDouble(), fixtureIndex, fixtureCount, bpm)));
        }

        return result;
    }

    // a capability value as the designer writes it (e.g. the color "Warm White"), without the properties missing
    private static ObjectNode capability(FixtureCapabilityValue value) {
        ObjectNode written = DesignerJson.MAPPER.valueToTree(value);
        ObjectNode capability = NODES.objectNode();

        for (String property : List.of("type", "color", "wheel", "profileUuid", "valuePercentage", "slotNumber")) {
            if (written.has(property) && !written.get(property).isNull()) {
                capability.set(property, written.get(property));
            }
        }

        return capability;
    }

    private static JsonNode state(PresetStepState state) {
        ObjectNode result = NODES.objectNode();
        result.put("step", state.getCurrentStep() == null ? null : state.getCurrentStep().getUuid());

        ArrayNode channels = result.putArray("channels");
        for (FixtureChannelValue value : state.getFixtureChannelValues()) {
            channels.addArray().add(value.getProfileUuid()).add(value.getChannelName()).add(number(value.getValue()));
        }

        ArrayNode capabilities = result.putArray("capabilities");
        state.getFixtureCapabilityValues().forEach(value -> capabilities.add(capability(value)));

        ObjectNode effects = result.putObject("effects");
        for (String effectUuid : new TreeSet<>(state.getEffectAmounts().keySet())) {
            effects.put(effectUuid, state.getEffectAmounts().get(effectUuid));
        }

        return result;
    }

    private JsonNode presetSteps(JsonNode input) {
        Preset preset = DesignerJson.MAPPER.treeToValue(input.get("preset"), Preset.class);
        Integer fixtureCount = integer(input.get("fixtureCount"));

        ObjectNode result = NODES.objectNode();
        result.put("loopMillis", PresetSteps.getStepsLoopMillis(preset));
        result.put("phased", PresetSteps.stepsArePhased(preset));

        ArrayNode samples = result.putArray("samples");

        for (JsonNode sample : input.get("samples")) {
            double phasingMillis = PresetSteps.getStepsPhasingMillis(preset, integer(sample.get("fixtureIndex")), fixtureCount);
            ObjectNode resultSample = samples.addObject();
            resultSample.put("phasingMillis", phasingMillis);
            resultSample.set("state", state(PresetSteps.getStateAtMillis(preset, sample.get("timeMillis").asDouble() - phasingMillis)));
        }

        return result;
    }

    private JsonNode presetIntensity(JsonNode input) {
        Preset preset = DesignerJson.MAPPER.treeToValue(input.get("preset"), Preset.class);
        ScenePlaybackRegion region = input.has("region") ? DesignerJson.MAPPER.treeToValue(input.get("region"), ScenePlaybackRegion.class) : null;
        Scene scene = input.has("scene") ? DesignerJson.MAPPER.treeToValue(input.get("scene"), Scene.class) : null;
        PresetRegionScene playing = new PresetRegionScene(preset, region, scene);

        ArrayNode intensities = NODES.arrayNode();
        input.get("times").forEach(time -> intensities.add(DefaultDesignerService.getPresetIntensity(playing, time.asDouble())));
        return intensities;
    }

    // a designer service with nothing to send to, for the pixels and the frames of the cases
    private DefaultDesignerService designerService() {
        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerLivePreview(false);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    private static ArrayNode keys(List<CachedFixturePixel> pixels) {
        ArrayNode keys = NODES.arrayNode();
        pixels.forEach(pixel -> keys.add(pixel.getKey()));
        return keys;
    }

    private JsonNode pixels(JsonNode input) {
        ObjectNode profileNode = NODES.objectNode();
        profileNode.set("matrix", input.get("matrix"));
        FixtureProfile profile = DesignerJson.MAPPER.treeToValue(profileNode, FixtureProfile.class);
        DefaultDesignerService designerService = designerService();
        DefaultFixturePixelGroupResolveService resolveService = new DefaultFixturePixelGroupResolveService();
        List<CachedFixturePixel> allPixels = designerService.getAllPixelKeys(profile);

        ObjectNode result = NODES.objectNode();
        ArrayNode pixels = result.putArray("pixels");
        allPixels.forEach(pixel -> pixels.addArray().add(pixel.getKey()).add(pixel.getX()).add(pixel.getY()).add(pixel.getZ()));

        ObjectNode groups = result.putObject("groups");
        if (profile.getMatrix().getPixelGroups() != null) {
            for (FixtureMatrixPixelGroup group : profile.getMatrix().getPixelGroups()) {
                groups.set(group.getName(), keys(resolveService.getPixelsForKeyOrGroup(profile.getMatrix(), allPixels, group.getName())));
            }
        }

        ArrayNode repeatFor = result.putArray("repeatFor");
        if (input.has("repeatFor")) {
            for (JsonNode entries : input.get("repeatFor")) {
                repeatFor.add(keys(designerService.getPixelsInOrder(profile, strings(entries))));
            }
        }

        return result;
    }

    private JsonNode pixelGroup(JsonNode input) {
        FixtureMatrixPixelGroup group = FixtureMatrixPixelGroupListDeserializer.readGroup("group", input.get("group"));
        List<CachedFixturePixel> pixels = new ArrayList<>();

        for (JsonNode pixelNode : input.get("pixels")) {
            CachedFixturePixel pixel = new CachedFixturePixel();
            pixel.setKey(pixelNode.get(0).asString());
            pixel.setX(pixelNode.get(1).asInt());
            pixel.setY(pixelNode.get(2).asInt());
            pixel.setZ(pixelNode.get(3).asInt());
            pixels.add(pixel);
        }

        try {
            return keys(new DefaultFixturePixelGroupResolveService().resolveGroupPixels(group, pixels));
        } catch (RuntimeException e) {
            // a rule which can't be read stops the whole group on both sides
            return NODES.stringNode("error");
        }
    }

    // The profiles named like the fixture library ("$profile": "stairville/mh-100") taken from the profiles of the cases
    private static JsonNode resolveProfiles(JsonNode project) {
        ObjectNode resolved = (ObjectNode) project.deepCopy();
        ArrayNode profiles = resolved.putArray("fixtureProfiles");

        for (JsonNode profile : project.path("fixtureProfiles")) {
            if (!profile.has("$profile")) {
                profiles.add(profile);
                continue;
            }

            String name = profile.get("$profile").asString();
            ObjectNode libraryProfile = (ObjectNode) DesignerJson.MAPPER.readTree(CONFORMANCE.resolve("profiles").resolve(name + ".json").toFile());
            libraryProfile.put("uuid", name);
            profiles.add(libraryProfile);
        }

        return resolved;
    }

    // The values the engine gives every fixture (and pixel) of a project at the passed times, the way it plays the
    // timeline of a composition, the selected preset or the selected scenes of the designer's live preview
    private JsonNode channelValues(JsonNode project, JsonNode preview, JsonNode times) {
        DefaultDesignerService designerService = designerService();
        designerService.load(null, DesignerJson.MAPPER.treeToValue(resolveProfiles(project), Project.class), null);

        String mode = preview.get("mode").asString();
        designerService.setPreviewPreset(false);
        designerService.setPreviewComposition(null);
        designerService.setSelectedSceneUuids(new ArrayList<>());

        switch (mode) {
            case "timeline" -> designerService.setPreviewComposition(preview.get("composition").asString());
            case "preset" -> {
                designerService.setPreviewPreset(true);
                designerService.setSelectedPresetUuid(text(preview.get("preset")));
                designerService.setSelectedStepUuid(text(preview.get("step")));

                if (preview.has("stepPreviewStartMillis")) {
                    designerService.setStepPreviewRunning(true, preview.get("stepPreviewStartMillis").asDouble());
                }
            }
            case "scenes" -> designerService.setSelectedSceneUuids(strings(preview.get("scenes")));
            default -> throw new IllegalArgumentException("Unknown preview mode " + mode);
        }

        // the fixtures identified from the fixture pool, if any
        designerService.setIdentifyFixtureUuids(preview.has("identify") ? strings(preview.get("identify")) : new ArrayList<>());

        ArrayNode frames = NODES.arrayNode();

        for (JsonNode time : times) {
            Map<CachedFixture, List<FixtureChannelValue>> values = designerService.calculateChannelValues(time.asDouble());
            ObjectNode frame = frames.addObject();

            for (CachedFixture cachedFixture : designerService.getCachedFixtures()) {
                ObjectNode fixtureValues = frame.putObject(cachedFixture.getFixture().getUuid() + (cachedFixture.getPixel() == null ? "" : "/" + cachedFixture.getPixel().getKey()));

                for (FixtureChannelValue value : values.get(cachedFixture)) {
                    fixtureValues.set(value.getChannelName(), number(value.getValue()));
                }
            }
        }

        return frames;
    }

    private JsonNode channelValues(JsonNode input) {
        ArrayNode runs = NODES.arrayNode();

        for (JsonNode run : input.get("runs")) {
            runs.add(channelValues(input.get("project"), run.get("preview"), run.get("times")));
        }

        return runs;
    }

    private static <T> List<T> listOf(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T> List<T> listOf(T[] array) {
        return array == null ? List.of() : Arrays.asList(array);
    }

    // a fixture of the project or of a preset: its uuid and its pixel (null for the fixture itself)
    private static ArrayNode presetFixtures(List<PresetFixture> presetFixtures) {
        ArrayNode result = NODES.arrayNode();
        listOf(presetFixtures).forEach(presetFixture -> result.addArray().add(presetFixture.getFixtureUuid()).add(presetFixture.getPixelKey()));
        return result;
    }

    private static ArrayNode texts(List<String> texts) {
        ArrayNode result = NODES.arrayNode();
        listOf(texts).forEach(result::add);
        return result;
    }

    // What both engines keep of a migrated project and play it with, like the designer writes it (conformance.spec.ts):
    // the fixtures and pixels of the presets, the steps, the curves, the layers of the scenes and the tracks of the
    // compositions. A clip names its track by its place in the composition: the uuid of a new track is a random one.
    private static JsonNode migratedProject(Project project) {
        ObjectNode result = NODES.objectNode();
        result.put("version", project.getVersion());
        result.set("presetFixtures", presetFixtures(project.getPresetFixtures()));

        ArrayNode presets = result.putArray("presets");
        for (Preset preset : listOf(project.getPresets())) {
            ObjectNode presetNode = presets.addObject();
            presetNode.put("uuid", preset.getUuid());
            presetNode.set("fixtures", presetFixtures(preset.getFixtures()));
            presetNode.put("useGlobalFixtureOrder", preset.isUseGlobalFixtureOrder());

            ArrayNode steps = presetNode.putArray("steps");
            for (PresetStep step : listOf(preset.getSteps())) {
                ObjectNode stepNode = steps.addObject();
                stepNode.put("startMillis", step.getStartMillis());
                stepNode.set("transitionMillis", number(step.getTransitionMillis()));
                stepNode.put("transitionCurve", step.getTransitionCurve());

                ArrayNode channels = stepNode.putArray("channels");
                listOf(step.getFixtureChannelValues()).forEach(value -> channels.addArray().add(value.getProfileUuid()).add(value.getChannelName()).add(number(value.getValue())));

                ArrayNode capabilities = stepNode.putArray("capabilities");
                listOf(step.getFixtureCapabilityValues()).forEach(value -> capabilities.add(capability(value)));

                ArrayNode effects = stepNode.putArray("effects");
                listOf(step.getEffectAmounts()).forEach(effectAmount -> effects.addArray().add(effectAmount.getEffectUuid()).add(effectAmount.getAmount()));
            }

            ArrayNode effects = presetNode.putArray("effects");
            for (Effect effect : listOf(preset.getEffects())) {
                ObjectNode effectNode = effects.addObject();
                effectNode.put("uuid", effect.getUuid());

                if (effect instanceof EffectCurve curve) {
                    effectNode.put("type", "curve");
                    effectNode.put("curveType", curve.getCurveType());
                    effectNode.put("amplitude", curve.getAmplitude());

                    ArrayNode channels = effectNode.putArray("channels");
                    listOf(curve.getChannels()).forEach(profileChannels -> channels.addArray().add(profileChannels.getProfileUuid()).add(texts(profileChannels.getChannels())));
                } else {
                    effectNode.put("type", "pan-tilt");
                }
            }
        }

        ArrayNode scenes = result.putArray("scenes");
        listOf(project.getScenes()).forEach(scene -> scenes.addArray().add(scene.getUuid()).add(texts(listOf(scene.getPresetUuids()))));

        ArrayNode compositions = result.putArray("compositions");
        for (Composition composition : listOf(project.getCompositions())) {
            ObjectNode compositionNode = compositions.addObject();
            compositionNode.put("uuid", composition.getUuid());

            List<String> trackUuids = composition.getTracks().stream().map(Track::getUuid).toList();
            ArrayNode tracks = compositionNode.putArray("tracks");
            composition.getTracks().forEach(track -> tracks.add(track.getName()));

            ArrayNode clips = compositionNode.putArray("clips");
            listOf(composition.getScenePlaybackRegions()).forEach(region -> clips.addArray().add(region.getSceneUuid()).add(trackUuids.indexOf(region.getTrackUuid())));
        }

        return result;
    }

    // A project of an older version migrated to the current one, like the engine does when it loads it
    private JsonNode migration(JsonNode input) {
        Project project = DesignerJson.MAPPER.treeToValue(resolveProfiles(input.get("project")), Project.class);
        designerService().migrateProject(project);
        return migratedProject(project);
    }

    // ---- the comparison ----

    // the first difference between what the designer recorded and what the engine came to, null for none
    static String difference(JsonNode expected, JsonNode actual, String path) {
        if (expected.isNumber() && actual.isNumber()) {
            // exactly the same number, the sign of a zero aside (JSON writes -0 as 0)
            return expected.asDouble() == actual.asDouble() ? null : path + ": expected " + expected + " but was " + actual;
        }

        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return path + ": expected " + expected + " but was " + actual;
            }

            for (int i = 0; i < expected.size(); i++) {
                String itemDifference = difference(expected.get(i), actual.get(i), path + "[" + i + "]");

                if (itemDifference != null) {
                    return itemDifference;
                }
            }

            return null;
        }

        if (expected.isObject() && actual.isObject()) {
            TreeSet<String> names = new TreeSet<>(expected.propertyNames());
            names.addAll(actual.propertyNames());

            for (String name : names) {
                if (!expected.has(name) || !actual.has(name)) {
                    return path + "." + name + ": expected " + expected.get(name) + " but was " + actual.get(name);
                }

                String propertyDifference = difference(expected.get(name), actual.get(name), path + "." + name);

                if (propertyDifference != null) {
                    return propertyDifference;
                }
            }

            return null;
        }

        return expected.equals(actual) ? null : path + ": expected " + expected + " but was " + actual;
    }

}
