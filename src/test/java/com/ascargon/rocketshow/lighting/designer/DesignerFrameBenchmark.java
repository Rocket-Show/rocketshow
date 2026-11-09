package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How long the engine takes to work out a frame of a rig of pixels and send it: 16 matrices of 16 x 8 RGB pixels
 * (2,048 pixels on 16 universes) lit by a pixel effect. Not run with the tests (its name doesn't end with Test), but
 * with mvn test -Dtest=DesignerFrameBenchmark. At 40 Hz, a frame has 25 ms.
 */
class DesignerFrameBenchmark {

    private static final int MATRICES = 16;

    @TempDir
    Path directory;

    private static ObjectNode project(JsonNodeFactory nodes) {
        ObjectNode project = nodes.objectNode();
        project.put("version", 11);
        project.put("name", "Benchmark");

        ObjectNode profile = project.putArray("fixtureProfiles").addObject();
        profile.put("uuid", "matrix");
        profile.put("name", "Matrix");
        profile.putObject("matrix").putArray("pixelCount").add(16).add(8).add(1);
        profile.putObject("availableChannels");
        ObjectNode templateChannels = profile.putObject("templateChannels");
        for (String color : List.of("Red", "Green", "Blue")) {
            ObjectNode channel = templateChannels.putObject(color + " $pixelKey");
            channel.putArray("fineChannelAliases");
            channel.putObject("capability").put("type", "ColorIntensity").put("color", color);
        }
        ObjectNode mode = profile.putArray("modes").addObject();
        mode.put("name", "384-channel");
        mode.put("shortName", "384ch");
        ObjectNode pixels = mode.putArray("channels").addObject();
        pixels.put("name", "");
        pixels.put("insert", "matrixChannels");
        pixels.putArray("repeatFor").add("eachPixelXYZ");
        pixels.put("channelOrder", "perPixel");
        pixels.putArray("templateChannels").add("Red $pixelKey").add("Green $pixelKey").add("Blue $pixelKey");

        ArrayNode fixtures = project.putArray("fixtures");
        ArrayNode presetFixtures = project.putArray("presetFixtures");
        ObjectNode preset = project.putArray("presets").addObject();
        preset.put("uuid", "pixels");
        preset.put("name", "Pixels");
        ArrayNode presetFixturesOfPreset = preset.putArray("fixtures");

        for (int i = 0; i < MATRICES; i++) {
            ObjectNode fixture = fixtures.addObject();
            fixture.put("uuid", "matrix-" + i);
            fixture.put("name", "Matrix " + i);
            fixture.put("profileUuid", "matrix");
            fixture.put("modeShortName", "384ch");
            fixture.put("dmxUniverseUuid", "universe-" + i);
            fixture.put("dmxFirstChannel", 0);

            for (int pixel = 1; pixel <= 128; pixel++) {
                presetFixtures.addObject().put("fixtureUuid", "matrix-" + i).put("pixelKey", String.valueOf(pixel));
                presetFixturesOfPreset.addObject().put("fixtureUuid", "matrix-" + i).put("pixelKey", String.valueOf(pixel));
            }
        }

        ObjectNode step = preset.putArray("steps").addObject();
        step.put("uuid", "step");
        ArrayNode values = step.putArray("fixtureCapabilityValues");
        values.addObject().put("type", "ColorIntensity").put("color", "Red").put("valuePercentage", 0.5);
        ObjectNode effect = preset.putArray("effects").addObject();
        effect.put("type", "pixel");
        effect.put("uuid", "plasma");
        effect.put("pattern", "plasma");
        effect.put("lengthMillis", 4000);
        effect.putObject("palette").put("ref", "rainbow").putArray("stops")
                .add(nodes.objectNode().put("position", 0).put("color", "#ff0000"))
                .add(nodes.objectNode().put("position", 0.5).put("color", "#00ff00"))
                .add(nodes.objectNode().put("position", 1).put("color", "#0000ff"));

        ObjectNode scene = project.putArray("scenes").addObject();
        scene.put("uuid", "scene");
        scene.put("name", "Scene");
        scene.putArray("presetUuids").add("pixels");
        project.putArray("compositions");

        return project;
    }

    @Test
    void worksOutAndSendsFramesOfTwoThousandPixels() throws Exception {
        Path designerPath = Files.createDirectories(directory.resolve("designer"));
        Files.writeString(designerPath.resolve("Project.json"), project(JsonNodeFactory.instance).toString());

        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);
        for (int i = 0; i < MATRICES; i++) {
            LightingUniverse universe = new LightingUniverse();
            universe.setUuid("universe-" + i);
            settings.getLightingUniverseList().add(universe);
        }

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
        designerService.load(null, designerService.getAllProjects().getFirst(), null);
        designerService.setSelectedSceneUuids(List.of("scene"));

        Method calculateUniverse = DefaultDesignerService.class.getDeclaredMethod("calculateUniverse", double.class);
        calculateUniverse.setAccessible(true);

        // warm up, then measure
        for (int frame = 0; frame < 200; frame++) {
            calculateUniverse.invoke(designerService, frame * 25.0);
        }

        int frames = 400;
        long start = System.nanoTime();

        for (int frame = 0; frame < frames; frame++) {
            calculateUniverse.invoke(designerService, frame * 25.0);
        }

        double millisPerFrame = (System.nanoTime() - start) / 1e6 / frames;

        // of which working out the values (the rest is sending them)
        start = System.nanoTime();

        for (int frame = 0; frame < frames; frame++) {
            designerService.calculateChannelValues(frame * 25.0);
        }

        double valuesMillisPerFrame = (System.nanoTime() - start) / 1e6 / frames;
        System.out.printf("BENCHMARK %d pixels: %.2f ms per frame, %.2f ms of it for the values%n", MATRICES * 128, millisPerFrame, valuesMillisPerFrame);
        designerService.close();
    }

}
