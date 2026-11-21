package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The conformance cases of the designer of the kind "migration" (web/projects/designer/conformance of the designer,
 * copied to src/test/resources/designer-conformance, see its README): a project of an older version migrated here
 * (see ProjectMigration) comes to the very same project as migrated by the designer, which recorded the expected
 * projects. What the lighting engine plays of a project is checked by its own conformance cases (see
 * LightingEngineConformanceTest and DesignerProjectJsonTest).
 */
class DesignerMigrationConformanceTest {

    private static final Path CONFORMANCE = Path.of("src/test/resources/designer-conformance");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @TestFactory
    Stream<DynamicContainer> migratesLikeTheDesigner() throws IOException {
        List<DynamicContainer> files = new ArrayList<>();

        try (Stream<Path> paths = Files.list(CONFORMANCE.resolve("cases"))) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonNode caseFile = DesignerJson.MAPPER.readTree(path.toFile());

                if (!caseFile.get("kind").asString().equals("migration")) {
                    continue;
                }

                List<DynamicTest> cases = new ArrayList<>();

                for (JsonNode conformanceCase : caseFile.get("cases")) {
                    cases.add(DynamicTest.dynamicTest(conformanceCase.get("name").asString(), () -> {
                        assertNotNull(conformanceCase.get("expected"), "Not recorded yet, see the README of the cases");
                        assertNull(difference(conformanceCase.get("expected"), migration(conformanceCase.get("input")), ""));
                    }));
                }

                files.add(DynamicContainer.dynamicContainer(path.getFileName().toString(), cases));
            }
        }

        return files.stream();
    }

    // A project of an older version migrated to the current one, like it is when it is loaded
    private static JsonNode migration(JsonNode input) {
        Project project = DesignerJson.MAPPER.treeToValue(resolveProfiles(input.get("project")), Project.class);
        ProjectMigration.migrate(project);
        return migratedProject(project);
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

    private static JsonNode number(Double value) {
        return value == null ? NODES.nullNode() : NODES.numberNode(value);
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

    // a value of a capability as the designer writes it, what is set of it
    private static ObjectNode capability(FixtureCapabilityValue value) {
        ObjectNode written = DesignerJson.MAPPER.valueToTree(value);
        ObjectNode capability = NODES.objectNode();

        for (String property : List.of("type", "color", "wheel", "profileUuid", "valuePercentage", "slotNumber",
                "valueDegrees", "spreadDegrees", "fanShape", "fan", "fanBetween", "shutterEffect", "programNumber", "soundControlled",
                "maintenanceAction", "aimX", "aimY", "aimZ", "blade")) {
            if (written.has(property) && !written.get(property).isNull()) {
                capability.set(property, written.get(property));
            }
        }

        return capability;
    }

    // What is kept of a migrated project, like the designer writes it (conformance.spec.ts): the fixtures and pixels of
    // the presets, the steps, the curves, the layers of the scenes and the tracks of the compositions. A clip names its
    // track by its place in the composition: the uuid of a new track is a random one.
    private static JsonNode migratedProject(Project project) {
        ObjectNode result = NODES.objectNode();
        result.put("version", project.getVersion());
        result.put("colorDimmersAtFull", project.isColorDimmersAtFull());
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

                if (effect instanceof EffectCurve curve && !(effect instanceof EffectPanTilt) && !(effect instanceof EffectPixel)) {
                    effectNode.put("type", "curve");
                    effectNode.put("curveType", curve.getCurveType());
                    effectNode.put("amplitude", curve.getAmplitude());

                    ArrayNode channels = effectNode.putArray("channels");
                    listOf(curve.getChannels()).forEach(profileChannels -> channels.addArray().add(profileChannels.getProfileUuid()).add(texts(profileChannels.getChannels())));
                } else if (effect instanceof EffectUnknown unknown) {
                    effectNode.put("type", unknown.getType());
                } else {
                    effectNode.put("type", effect instanceof EffectPixel ? "pixel" : "pan-tilt");
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

    // the first difference between what the designer recorded and what was migrated here, null for none
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
