package com.ascargon.rocketshow.lighting.engine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * engine.wasm of the shared lighting engine in the JVM comes to the very same bits as the native build of the engine,
 * for every value of every frame of the channelValues cases of the designer: the digests the engine recorded from its
 * native build (conformance/core-digests.json of the engine, copied to src/test/resources/lighting-engine) are compared
 * with the ones of the cases played here (this repository's copy, src/test/resources/designer-conformance), the way
 * tools/conformance-wasm.mjs of the engine plays them in Node.
 */
public class LightingEngineConformanceTest {

    private static final Path CONFORMANCE = Path.of("src/test/resources/designer-conformance");
    private static final Path DIGESTS = Path.of("src/test/resources/lighting-engine/core-digests.json");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static LightingEngine engine;

    @BeforeAll
    public static void loadTheEngine() {
        engine = LightingEngine.loadPacked();
    }

    /**
     * A digest of the bits of values: two FNV-1a hashes of 32 bits with different starts, over the 4 bytes of each value
     * (0 for a channel without one) and a byte saying whether the channel has a value. The one of the engine
     * (crates/compiler/tests/conformance_channel_values.rs and tools/conformance-wasm.mjs).
     */
    static class Digest {

        private int a = 0x811c9dc5;
        private int b = 0x01000193;

        private void add(int value) {
            a = (a ^ value) * 16777619;
            b = (b ^ value) * 16777619;
        }

        void frame(EngineShow show) {
            int count = show.getValueCount();
            int[] values = new int[count];
            int[] flags = new int[count];
            show.readValues(values, flags);

            for (int value = 0; value < count; value++) {
                int set = flags[value] & 1;

                for (int shift = 0; shift < 32; shift += 8) {
                    add(set == 0 ? 0 : (values[value] >>> shift) & 0xff);
                }

                add(set);
            }
        }

        String text() {
            return String.format("%08x%08x", a, b);
        }

    }

    static long micros(double millis) {
        return Math.round(millis * 1000);
    }

    // the project of a case with the fixture profiles it names ("$profile") read from the profiles of the cases
    public static String resolveProfiles(JsonNode project) throws IOException {
        ObjectNode resolved = ((ObjectNode) project).deepCopy();
        ArrayNode profiles = MAPPER.createArrayNode();

        if (project.has("fixtureProfiles")) {
            for (JsonNode profile : project.get("fixtureProfiles")) {
                if (profile.has("$profile")) {
                    String name = profile.get("$profile").asString();
                    ObjectNode loaded = (ObjectNode) MAPPER.readTree(CONFORMANCE.resolve("profiles").resolve(name + ".json").toFile());
                    loaded.put("uuid", name);
                    profiles.add(loaded);
                } else {
                    profiles.add(profile);
                }
            }
        }

        resolved.set("fixtureProfiles", profiles);
        return MAPPER.writeValueAsString(resolved);
    }

    // play a run of a case: what plays, the holds, the fixtures marked and the audio, and a frame at each time
    static void play(CompiledShow compiled, JsonNode run, JsonNode audio, Digest digest) {
        ShowIndex index = compiled.index();

        try (EngineShow show = engine.play(compiled.show(), index, 0)) {
            JsonNode preview = run.get("preview");
            String mode = preview.get("mode").asString();

            if (mode.equals("timeline")) {
                show.playTimeline(index.compositionNamed(preview.get("composition").asString()), 0);
            } else if (mode.equals("preset")) {
                show.playPreview(true);
                int preset = preview.hasNonNull("preset") ? index.preset(preview.get("preset").asString()) : -1;
                int step = preset < 0 || !preview.hasNonNull("step") ? -1 : index.step(preset, preview.get("step").asString());
                show.selectPreset(preset, step);

                if (preview.has("stepPreviewStartMillis")) {
                    show.setStepPreview(true, micros(preview.get("stepPreviewStartMillis").asDouble()));
                }
            } else {
                show.playPreview(false);

                if (preview.has("scenes")) {
                    for (JsonNode uuid : preview.get("scenes")) {
                        int scene = index.scene(uuid.asString());

                        if (scene >= 0) {
                            show.selectScene(scene, true);
                        }
                    }
                }
            }

            if (run.has("holds")) {
                for (JsonNode hold : run.get("holds")) {
                    show.addHold(micros(hold.get("positionMillis").asDouble()), micros(hold.get("heldMillis").asDouble()));
                }
            }

            Map<String, BiConsumer<Integer, Boolean>> marks = Map.of("identify", show::identify, "reset", show::resetFixture, "lampOn", show::lampOn);

            for (Map.Entry<String, BiConsumer<Integer, Boolean>> mark : marks.entrySet()) {
                if (preview.has(mark.getKey())) {
                    for (JsonNode uuid : preview.get(mark.getKey())) {
                        for (int fixture : index.fixtureNumbers(uuid.asString())) {
                            mark.getValue().accept(fixture, true);
                        }
                    }
                }
            }

            List<double[]> frames = new ArrayList<>();

            if (audio != null && audio.has("frames")) {
                for (JsonNode frame : audio.get("frames")) {
                    double[] values = new double[frame.size()];

                    for (int value = 0; value < values.length; value++) {
                        values[value] = frame.get(value).asDouble();
                    }

                    frames.add(values);
                }
            }

            for (JsonNode time : run.get("times")) {
                long position = micros(time.asDouble());
                long now = Math.max(position, 0);

                if (!frames.isEmpty()) {
                    show.clearAudio();
                    int current = (int) (now / 20000);

                    for (int frame = Math.max(current - 300, 0); frame <= current; frame++) {
                        if (frame < frames.size()) {
                            show.audioFrame(frame, frames.get(frame));
                        }
                    }
                }

                show.setPosition(now, position, false);
                show.render(now);
                digest.frame(show);
            }
        }
    }

    // the digest of all frames of all cases of a file
    public static String digestOf(Path file, java.util.function.Function<JsonNode, String> projectJson) throws IOException {
        Digest digest = new Digest();

        for (JsonNode conformanceCase : MAPPER.readTree(file.toFile()).get("cases")) {
            JsonNode input = conformanceCase.get("input");
            CompiledShow compiled = engine.compile(projectJson.apply(input.get("project")));
            assertEquals(CompiledShow.SHOW, compiled.code(), () -> conformanceCase.get("name").asString() + ": " + compiled.message());

            for (JsonNode run : input.get("runs")) {
                play(compiled, run, input.get("audio"), digest);
            }
        }

        return digest.text();
    }

    @TestFactory
    Stream<DynamicTest> comesToTheBitsOfTheNativeBuild() throws IOException {

        JsonNode recorded = MAPPER.readTree(DIGESTS.toFile());
        List<DynamicTest> tests = new ArrayList<>();

        for (Map.Entry<String, JsonNode> file : recorded.properties()) {
            tests.add(DynamicTest.dynamicTest(file.getKey(), () -> assertEquals(file.getValue().asString(), digestOf(CONFORMANCE.resolve("cases").resolve(file.getKey()), project -> {
                try {
                    return resolveProfiles(project);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }))));
        }

        return tests.stream();
    }

}
