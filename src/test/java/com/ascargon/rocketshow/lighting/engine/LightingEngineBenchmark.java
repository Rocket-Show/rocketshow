package com.ascargon.rocketshow.lighting.engine;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * How long engine.wasm of the shared lighting engine takes in the JVM: loading it (compiled to bytecode, and
 * interpreted to compare), compiling a project and working out a frame, cold and warmed up, on the rigs of the engine's
 * own benchmark (crates/compiler/examples/bench.rs of the engine): 30 and 60 moving heads (17 channels each) playing two
 * scenes with fans, a chase, a movement and steps, and 16 matrices of 16 x 8 RGB pixels lit by a pixel effect. A frame
 * here is what the send timer would do: render, then copy the universes out of the module. At 40 Hz, a frame has 25 ms;
 * the gate of the spike is 5 ms for the 60 moving heads on SPARK's hardware (see docs/lighting-engine.md).
 * <p>
 * Not run with the tests (its name doesn't end with Test), but with ./mvnw test -Dtest=LightingEngineBenchmark
 * -Dskip.npm -Dskip.installnodenpm (needs engine.wasm, see testing/build-lighting-engine.sh).
 */
public class LightingEngineBenchmark {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Path PROFILES = Path.of("src/test/resources/designer-conformance/profiles");

    private static ObjectNode capability(String type) {
        return MAPPER.createObjectNode().put("type", type);
    }

    // count moving heads (17 channels), 30 on a universe, playing two scenes (see heads() of bench.rs)
    public static String heads(int count) throws IOException {
        ObjectNode project = MAPPER.createObjectNode();
        project.put("version", 11);
        ObjectNode profile = (ObjectNode) MAPPER.readTree(PROFILES.resolve("dts/xr4-spot.json").toFile());
        profile.put("uuid", "dts/xr4-spot");
        project.putArray("fixtureProfiles").add(profile);

        ArrayNode fixtures = project.putArray("fixtures");
        ArrayNode all = MAPPER.createArrayNode();
        ArrayNode half = MAPPER.createArrayNode();

        for (int index = 0; index < count; index++) {
            fixtures.addObject().put("uuid", "head-" + index).put("profileUuid", "dts/xr4-spot").put("modeShortName", "17ch")
                    .put("dmxUniverseUuid", "universe-" + index / 30).put("dmxFirstChannel", (index % 30) * 17);
            all.addObject().put("fixtureUuid", "head-" + index);

            if (index % 2 == 0) {
                half.addObject().put("fixtureUuid", "head-" + index);
            }
        }

        project.set("presetFixtures", all.deepCopy());
        ArrayNode presets = project.putArray("presets");

        ObjectNode look = presets.addObject().put("uuid", "look");
        look.set("fixtures", all.deepCopy());
        ArrayNode lookValues = look.putArray("steps").addObject().put("uuid", "look-step").put("startMillis", 0).putArray("fixtureCapabilityValues");
        ObjectNode intensity = capability("Intensity").put("valuePercentage", 0.5);
        intensity.putArray("fan").add(MAPPER.createObjectNode().put("position", 0).put("valuePercentage", 0.2)).add(MAPPER.createObjectNode().put("position", 1).put("valuePercentage", 1));
        lookValues.add(intensity);
        lookValues.add(capability("ColorIntensity").put("color", "Red").put("valuePercentage", 1));
        lookValues.add(capability("ColorIntensity").put("color", "Green").put("valuePercentage", 0.3));
        lookValues.add(capability("ColorIntensity").put("color", "Blue").put("valuePercentage", 0.1));
        lookValues.add(capability("Pan").put("valueDegrees", 0).put("spreadDegrees", 120));
        lookValues.add(capability("Tilt").put("valueDegrees", 30));
        lookValues.add(capability("Zoom").put("valuePercentage", 0.4));
        lookValues.add(capability("ShutterStrobe").put("valuePercentage", 0));
        ArrayNode lookEffects = look.putArray("effects");
        ObjectNode chase = lookEffects.addObject().put("type", "curve").put("uuid", "chase").put("curveType", "sine").put("lengthMillis", 2000).put("phasingMode", "spread");
        chase.putArray("capabilities").add(capability("Intensity"));
        lookEffects.addObject().put("type", "pan-tilt").put("uuid", "circle").put("shape", "circle").put("lengthMillis", 4000).put("phasingMode", "spread").put("fanMode", "linear");

        ObjectNode steps = presets.addObject().put("uuid", "steps").put("stepsLoop", true).put("stepsPhasingMode", "spread");
        steps.set("fixtures", half);
        ArrayNode stepList = steps.putArray("steps");
        ObjectNode a = stepList.addObject().put("uuid", "a").put("startMillis", 0).put("transitionMillis", 500);
        a.putArray("fixtureCapabilityValues").add(capability("ColorIntensity").put("color", "Red").put("valuePercentage", 0))
                .add(capability("ColorIntensity").put("color", "Blue").put("valuePercentage", 1)).add(capability("Iris").put("valuePercentage", 0.2));
        ObjectNode b = stepList.addObject().put("uuid", "b").put("startMillis", 1000).put("transitionMillis", 500);
        b.putArray("fixtureCapabilityValues").add(capability("ColorIntensity").put("color", "Red").put("valuePercentage", 1))
                .add(capability("ColorIntensity").put("color", "Blue").put("valuePercentage", 0)).add(capability("Iris").put("valuePercentage", 1));
        ObjectNode c = stepList.addObject().put("uuid", "c").put("startMillis", 2000).put("transitionMillis", 500);
        c.putArray("fixtureCapabilityValues").add(capability("ColorIntensity").put("color", "Green").put("valuePercentage", 1)).add(capability("Iris").put("valuePercentage", 0.6));
        ObjectNode zoom = steps.putArray("effects").addObject().put("type", "curve").put("uuid", "zoom").put("curveType", "triangle").put("lengthMode", "beats").put("lengthBeats", 2);
        zoom.putArray("capabilities").add(capability("Zoom"));

        ArrayNode scenes = project.putArray("scenes");
        scenes.addObject().put("uuid", "base").putArray("presetUuids").add("look");
        scenes.addObject().put("uuid", "top").put("dimmer", 0.8).putArray("presetUuids").add("steps");

        return MAPPER.writeValueAsString(project);
    }

    // count matrices of 16 x 8 RGB pixels on a universe each, lit by a plasma (see matrices() of bench.rs)
    static String matrices(int count) {
        ObjectNode project = MAPPER.createObjectNode();
        project.put("version", 11);
        ObjectNode profile = project.putArray("fixtureProfiles").addObject();
        profile.put("uuid", "matrix");
        profile.putObject("matrix").putArray("pixelCount").add(16).add(8).add(1);
        profile.putObject("availableChannels");
        ObjectNode templateChannels = profile.putObject("templateChannels");

        for (String color : List.of("Red", "Green", "Blue")) {
            ObjectNode channel = templateChannels.putObject(color + " $pixelKey");
            channel.putArray("fineChannelAliases");
            channel.putObject("capability").put("type", "ColorIntensity").put("color", color);
        }

        ObjectNode mode = profile.putArray("modes").addObject().put("name", "384-channel").put("shortName", "384ch");
        ObjectNode pixelChannels = mode.putArray("channels").addObject().put("name", "").put("insert", "matrixChannels").put("channelOrder", "perPixel");
        pixelChannels.putArray("repeatFor").add("eachPixelXYZ");
        pixelChannels.putArray("templateChannels").add("Red $pixelKey").add("Green $pixelKey").add("Blue $pixelKey");

        ArrayNode fixtures = project.putArray("fixtures");
        ArrayNode pixels = MAPPER.createArrayNode();

        for (int index = 0; index < count; index++) {
            fixtures.addObject().put("uuid", "matrix-" + index).put("profileUuid", "matrix").put("modeShortName", "384ch").put("dmxUniverseUuid", "universe-" + index).put("dmxFirstChannel", 0);

            for (int y = 1; y <= 8; y++) {
                for (int x = 1; x <= 16; x++) {
                    pixels.addObject().put("fixtureUuid", "matrix-" + index).put("pixelKey", "(" + x + ", " + y + ")");
                }
            }
        }

        project.set("presetFixtures", pixels.deepCopy());
        ObjectNode preset = project.putArray("presets").addObject().put("uuid", "pixels");
        preset.set("fixtures", pixels);
        preset.putArray("steps").addObject().put("uuid", "step").putArray("fixtureCapabilityValues").add(capability("ColorIntensity").put("color", "Red").put("valuePercentage", 0.5));
        ObjectNode plasma = preset.putArray("effects").addObject().put("type", "pixel").put("uuid", "plasma").put("pattern", "plasma").put("lengthMillis", 4000);
        plasma.putObject("palette").put("ref", "rainbow").putArray("stops")
                .add(MAPPER.createObjectNode().put("position", 0).put("color", "#ff0000"))
                .add(MAPPER.createObjectNode().put("position", 0.5).put("color", "#00ff00"))
                .add(MAPPER.createObjectNode().put("position", 1).put("color", "#0000ff"));
        project.putArray("scenes").addObject().put("uuid", "scene").putArray("presetUuids").add("pixels");

        return MAPPER.writeValueAsString(project);
    }

    private static double median(double[] times) {
        double[] sorted = times.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1e6;
    }

    // a frame as the send timer would work it out: render, then copy the universes out of the module
    private static double frame(EngineShow show, byte[] universes, long nowMicros) {
        long start = System.nanoTime();
        show.render(nowMicros);
        show.readUniverses(universes);
        return millisSince(start);
    }

    private static void measure(LightingEngine engine, String name, String project) {
        // compiling the project, as loading it costs it
        double[] compileTimes = new double[15];
        CompiledShow compiled = null;

        for (int time = 0; time < compileTimes.length; time++) {
            long start = System.nanoTime();
            compiled = engine.compile(project);
            compileTimes[time] = millisSince(start);
        }

        assertTrue(compiled.isShow(), compiled.message());

        try (EngineShow show = engine.play(compiled.show(), compiled.index(), 0)) {
            show.playPreview(false);

            for (int scene = 0; scene < compiled.index().scenes().size(); scene++) {
                show.selectScene(scene, true);
            }

            show.setStepPreview(true, 0);
            show.setPosition(0, 0, true);
            byte[] universes = new byte[show.getUniverseCount() * EngineShow.UNIVERSE_SIZE];

            // cold: the first frame, and the first 200 frames 25 ms apart (the warm-up of a load)
            long frame = 0;
            double first = frame(show, universes, 0);
            long start = System.nanoTime();

            for (frame = 1; frame <= 200; frame++) {
                frame(show, universes, frame * 25_000);
            }

            double warmUp = millisSince(start);

            // warmed up: frames 25 ms apart, measured one by one and all together
            int frames = 4000;
            double[] times = new double[frames];
            long sum = 0;
            start = System.nanoTime();

            for (int measured = 0; measured < frames; measured++, frame++) {
                times[measured] = frame(show, universes, frame * 25_000);
                sum += universes[measured % universes.length];
            }

            double perFrame = millisSince(start) / frames;
            double[] sorted = times.clone();
            Arrays.sort(sorted);

            System.out.printf("BENCHMARK %s: %d universes | compile %.2f ms | first frame %.2f ms, first 200 frames %.1f ms (%.3f ms each) | warm: %.4f ms a frame, median %.4f ms, 99th percentile %.4f ms, max %.3f ms (%d)%n",
                    name, show.getUniverseCount(), median(compileTimes), first, warmUp, warmUp / 200, perFrame, median(times), sorted[(int) (frames * 0.99)], sorted[frames - 1], sum);
        }
    }

    @Test
    void measuresTheFramesOfTheRigsOfTheEngine() throws IOException {
        byte[] wasm = LightingEngine.readPacked();
        assumeTrue(wasm != null, "No engine.wasm in this build, see testing/build-lighting-engine.sh");

        System.out.printf("BENCHMARK Java %s (%s) on %s %s, %d processors, engine.wasm %d KB of revision %s%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"), System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), wasm.length / 1024, LightingEngine.packedRevision());

        for (boolean compiled : List.of(true, false)) {
            long start = System.nanoTime();
            LightingEngine engine = new LightingEngine(wasm, compiled);
            System.out.printf("BENCHMARK %s: loaded in %.0f ms%n", compiled ? "compiled to bytecode" : "interpreted", millisSince(start));

            measure(engine, (compiled ? "compiled" : "interpreted") + ", 30 moving heads", heads(30));
            measure(engine, (compiled ? "compiled" : "interpreted") + ", 60 moving heads", heads(60));

            if (compiled) {
                measure(engine, "compiled, 2048 pixels", matrices(16));
            }
        }
    }

}
