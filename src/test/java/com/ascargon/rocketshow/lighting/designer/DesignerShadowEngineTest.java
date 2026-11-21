package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.engine.LightingEngine;
import com.ascargon.rocketshow.lighting.engine.ShadowEngine;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shadow mode played over the channelValues cases of the designer: the designer service plays each case the way the
 * designer previews it (like DesignerConformanceTest), with the shared lighting engine beside it, and the universes of
 * the two are compared frame by frame (see ShadowEngine). The two engines come to the same universes within 1 but for
 * the values listed in conformance/DIFFERENCES.md of the engine, which come from the rounding of doubles on an edge.
 * <p>
 * Skipped without engine.wasm (see testing/build-lighting-engine.sh).
 */
class DesignerShadowEngineTest {

    private static final Path CONFORMANCE = Path.of("src/test/resources/designer-conformance");

    // The cases listed in conformance/DIFFERENCES.md of the engine, where a value of the Java engine sits on an edge
    // decided by the rounding of a double. Any other case differing fails the test, and so does a listed one which
    // doesn't differ anymore, to keep the list true.
    private static final Set<String> DIFFERING = Set.of(
            "channel-values-pixel-effects.json/comet-2",
            "channel-values-pixel-effects.json/image-1",
            "channel-values-pixel-effects.json/meter-1",
            "channel-values-pixel-effects.json/scope-1");

    @TempDir
    Path directory;

    private static List<String> strings(JsonNode node) {
        List<String> strings = new ArrayList<>();

        if (node != null) {
            node.forEach(item -> strings.add(item.asString()));
        }

        return strings;
    }

    private static JsonNode resolveProfiles(JsonNode project) throws IOException {
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

    // a designer service with shadow mode on, its universes the ones the fixtures of the project are on
    private DefaultDesignerService designerService(Project project) {
        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerLivePreview(false);
        settings.setDesignerShadowEngine(true);

        Set<String> universeUuids = new LinkedHashSet<>();

        for (Fixture fixture : project.getFixtures()) {
            if (fixture.getDmxUniverseUuid() != null) {
                universeUuids.add(fixture.getDmxUniverseUuid());
            }
        }

        for (String universeUuid : universeUuids) {
            LightingUniverse universe = new LightingUniverse();
            universe.setUuid(universeUuid);
            settings.getLightingUniverseList().add(universe);
        }

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    // play a run of a case like the designer previews it, with shadow mode on: the frames which differed
    private long playRun(JsonNode input, JsonNode run) throws Exception {
        Project project = DesignerJson.MAPPER.treeToValue(resolveProfiles(input.get("project")), Project.class);
        DefaultDesignerService designerService = designerService(project);
        designerService.load(null, project, null);
        ShadowEngine shadow = designerService.getShadowEngine();
        assertNotNull(shadow, "Shadow mode is on");

        JsonNode audio = input.get("audio");

        if (audio != null && audio.has("frames")) {
            int index = 0;

            for (JsonNode frame : audio.get("frames")) {
                double[] values = new double[frame.size()];

                for (int i = 0; i < values.length; i++) {
                    values[i] = frame.get(i).asDouble();
                }

                designerService.getAudioAnalysisService().setFrame("", index++, values);
            }

            designerService.getAudioAnalysisService().setRequiredBuses(List.of(""));
        }

        JsonNode preview = run.get("preview");
        String mode = preview.get("mode").asString();

        switch (mode) {
            case "timeline" -> designerService.setPreviewComposition(preview.get("composition").asString());
            case "preset" -> {
                designerService.setPreviewPreset(true);
                designerService.setSelectedPresetUuid(preview.hasNonNull("preset") ? preview.get("preset").asString() : null);
                designerService.setSelectedStepUuid(preview.hasNonNull("step") ? preview.get("step").asString() : null);

                if (preview.has("stepPreviewStartMillis")) {
                    designerService.setStepPreviewRunning(true, preview.get("stepPreviewStartMillis").asDouble());
                }
            }
            default -> designerService.setSelectedSceneUuids(strings(preview.get("scenes")));
        }

        List<CompositionHold> holds = new ArrayList<>();

        for (JsonNode hold : run.path("holds")) {
            holds.add(new CompositionHold(hold.get("positionMillis").asDouble(), hold.get("heldMillis").asDouble()));
        }

        designerService.setPreviewHolds(holds, false);
        designerService.setIdentifyFixtureUuids(strings(preview.get("identify")));
        designerService.setMaintenanceFixtureUuids(strings(preview.get("reset")), strings(preview.get("lampOn")));

        Method calculateUniverse = DefaultDesignerService.class.getDeclaredMethod("calculateUniverse", double.class);
        calculateUniverse.setAccessible(true);

        for (JsonNode time : run.get("times")) {
            calculateUniverse.invoke(designerService, time.asDouble());
            // (no frame dropped)
            shadow.awaitIdle();
        }

        assertFalse(shadow.isFailed(), "The shared engine failed");
        assertEquals(run.get("times").size(), shadow.getFramesCompared());

        long differing = shadow.getFramesDiffering();
        shadow.close();
        return differing;
    }

    @TestFactory
    Stream<DynamicContainer> comesToTheUniversesOfTheJavaEngine() throws IOException {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build, see testing/build-lighting-engine.sh");

        List<DynamicContainer> files = new ArrayList<>();

        try (Stream<Path> paths = Files.list(CONFORMANCE.resolve("cases"))) {
            for (Path path : paths.filter(path -> path.getFileName().toString().startsWith("channel-values-")).sorted().toList()) {
                List<DynamicTest> cases = new ArrayList<>();

                for (JsonNode conformanceCase : DesignerJson.MAPPER.readTree(path.toFile()).get("cases")) {
                    String name = path.getFileName() + "/" + conformanceCase.get("name").asString();

                    cases.add(DynamicTest.dynamicTest(conformanceCase.get("name").asString(), () -> {
                        long differing = 0;

                        for (JsonNode run : conformanceCase.get("input").get("runs")) {
                            differing += playRun(conformanceCase.get("input"), run);
                        }

                        if (DIFFERING.contains(name)) {
                            assertTrue(differing > 0, "Listed as differing, but the engines come to the same universes");
                        } else {
                            assertEquals(0, differing, "Frames differing by more than 1");
                        }
                    }));
                }

                files.add(DynamicContainer.dynamicContainer(path.getFileName().toString(), cases));
            }
        }

        return files.stream();
    }

}
