package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.engine.LightingEngine;
import com.ascargon.rocketshow.lighting.engine.LightingEngineBenchmark;
import com.ascargon.rocketshow.lighting.engine.ShadowEngine;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shadow mode while Rocket Show plays: the timeline of a composition with a hold, the scenes picked by hand with their
 * fades, dimmers, speed, master dimmer and the tempo from outside, the faders of the designer, the fixtures identified.
 * The shared engine comes to the universes of the Java engine within 1 throughout. And whatever shadow mode does, the
 * Java engine plays on: with it off (the default), without engine.wasm and with a shared engine which fails.
 * <p>
 * The frames are worked out here one after the other (the send timer isn't started), on the real clock.
 */
class DesignerShadowModeTest {

    @TempDir
    Path directory;

    private final List<LightingUniverseState> lightingUniverses = new CopyOnWriteArrayList<>();

    // 8 moving heads on a universe playing two scenes (see LightingEngineBenchmark), the top one fading, a scene
    // flashed, and a composition playing them, with a hold point
    private static Project project() throws Exception {
        ObjectNode project = (ObjectNode) DesignerJson.MAPPER.readTree(LightingEngineBenchmark.heads(8));
        project.put("name", "Shadow");
        ArrayNode scenes = (ArrayNode) project.get("scenes");
        ((ObjectNode) scenes.get(1)).put("fadeInMillis", 300).put("fadeOutMillis", 300);
        scenes.addObject().put("uuid", "flash").putArray("presetUuids").add("steps");

        ObjectNode composition = project.putArray("compositions").addObject();
        composition.put("uuid", "song").put("name", "Song").put("beatsPerMinute", 128);
        composition.putArray("tracks").add(DesignerJson.MAPPER.createObjectNode().put("uuid", "t0").put("name", "Base"))
                .add(DesignerJson.MAPPER.createObjectNode().put("uuid", "t1").put("name", "Top"));
        ArrayNode regions = composition.putArray("scenePlaybackRegions");
        regions.addObject().put("sceneUuid", "base").put("startMillis", 0).put("endMillis", 8000).put("trackUuid", "t0");
        regions.addObject().put("sceneUuid", "top").put("startMillis", 1000).put("endMillis", 6000).put("trackUuid", "t1");
        composition.putArray("markers").addObject().put("uuid", "hold").put("positionMillis", 3000).put("type", "hold");

        return DesignerJson.MAPPER.treeToValue(project, Project.class);
    }

    private DefaultDesignerService designerService(Boolean shadowMode) {
        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerLivePreview(false);

        if (shadowMode != null) {
            settings.setDesignerShadowEngine(shadowMode);
        }

        LightingUniverse universe = new LightingUniverse();
        universe.setUuid("universe-0");
        settings.getLightingUniverseList().add(universe);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        LightingService lightingService = mock(LightingService.class);
        doAnswer(invocation -> lightingUniverses.add(invocation.getArgument(0))).when(lightingService).addLightingUniverse(any());
        doAnswer(invocation -> lightingUniverses.remove((LightingUniverseState) invocation.getArgument(0))).when(lightingService).removeLightingUniverse(any());

        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), lightingService, new DefaultFixturePixelGroupResolveService());
    }

    // a frame worked out like the send timer does, at the passed position (null: the one of the service now), and
    // compared by shadow mode before the next one
    private static void frame(DefaultDesignerService designerService, Double positionMillis) throws Exception {
        long now = System.currentTimeMillis();
        double position;

        if (positionMillis != null) {
            position = positionMillis;
        } else {
            Method getCurrentPositionMillis = DefaultDesignerService.class.getDeclaredMethod("getCurrentPositionMillis", long.class);
            getCurrentPositionMillis.setAccessible(true);
            position = (double) getCurrentPositionMillis.invoke(designerService, now);
        }

        Method calculateUniverse = DefaultDesignerService.class.getDeclaredMethod("calculateUniverse", double.class, long.class);
        calculateUniverse.setAccessible(true);
        calculateUniverse.invoke(designerService, position, now);

        ShadowEngine shadow = designerService.getShadowEngine();

        if (shadow != null) {
            shadow.awaitIdle();
        }
    }

    private static void assertNoDifferences(ShadowEngine shadow, long frames) {
        assertFalse(shadow.isFailed(), "The shared engine failed");
        assertEquals(frames, shadow.getFramesCompared());
        assertEquals(0, shadow.getFramesDiffering(), "Frames differing by more than 1, up to " + shadow.getLargestDifference());
    }

    private int sentValue(int channel) {
        Integer value = lightingUniverses.getFirst().getUniverse().get(channel);
        return value == null ? 0 : value;
    }

    @Test
    void comparesTheTimelineOfACompositionWithAHold() throws Exception {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build");

        com.ascargon.rocketshow.composition.Composition playing = new com.ascargon.rocketshow.composition.Composition();
        playing.setName("Song");
        CompositionPlayer player = mock(CompositionPlayer.class);
        when(player.getComposition()).thenReturn(playing);
        when(player.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);

        DefaultDesignerService designerService = designerService(true);
        designerService.load(player, project(), null);
        ShadowEngine shadow = designerService.getShadowEngine();
        assertNotNull(shadow);
        long frames = 0;

        // up to the hold point, held there for a while (the clips run on), then on from there
        for (double position = 0; position < 3000; position += 25, frames++) {
            frame(designerService, position);
        }

        designerService.hold(3000);

        for (int held = 0; held < 20; held++, frames++) {
            frame(designerService, 3000.0);
            Thread.sleep(10);
        }

        designerService.endHold();

        for (double position = 3000; position < 7000; position += 25, frames++) {
            frame(designerService, position);
        }

        assertNoDifferences(shadow, frames);
        shadow.close();
    }

    @Test
    void comparesTheScenesPickedByHand() throws Exception {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build");

        DefaultDesignerService designerService = designerService(true);
        Project project = project();
        long frames = 0;

        designerService.playManualScenes(project, List.of("base"), 0.8);
        ShadowEngine shadow = designerService.getShadowEngine();
        assertNotNull(shadow);

        for (int frame = 0; frame < 20; frame++, frames++) {
            frame(designerService, null);
            Thread.sleep(5);
        }

        // a scene fading in, another flashed, the dimmers and the speed of the Play screen, a tempo tapped
        designerService.playManualScenes(project, List.of("base", "top"), List.of("flash"), 0.6);
        designerService.setLiveSceneDimmers(Map.of("top", 0.5));
        designerService.setOutsideBeatsPerMinute(140.0);

        for (int frame = 0; frame < 40; frame++, frames++) {
            frame(designerService, null);

            if (frame == 10) {
                designerService.setLiveSpeed(2);
                designerService.getBeatClock().alignBeat(System.currentTimeMillis());
            }

            if (frame == 20) {
                designerService.playManualScenes(project, List.of("top"), List.of(), 1);
                designerService.setLiveSpeed(1);
            }

            Thread.sleep(5);
        }

        // let go: they fade out
        designerService.releaseManualScenes();

        for (int frame = 0; frame < 20; frame++, frames++) {
            frame(designerService, null);
            Thread.sleep(5);
        }

        assertNoDifferences(shadow, frames);
        shadow.close();
    }

    @Test
    void comparesThePreviewOfTheDesignerWithFadersAndFixturesIdentified() throws Exception {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build");

        DefaultDesignerService designerService = designerService(true);
        designerService.load(null, project(), null);
        ShadowEngine shadow = designerService.getShadowEngine();
        long frames = 0;

        designerService.setSelectedSceneUuids(List.of("base", "top"));

        for (double position = 0; position < 1000; position += 25, frames++) {
            frame(designerService, position);
        }

        DmxOverride fader = new DmxOverride();
        fader.setUniverseUuid("universe-0");
        fader.setDmxChannel(5);
        fader.setValue(200);
        designerService.setDmxOverrides(List.of(fader));
        designerService.setIdentifyFixtureUuids(List.of("head-1"));
        designerService.setMaintenanceFixtureUuids(List.of("head-2"), List.of("head-3"));

        for (double position = 1000; position < 2000; position += 25, frames++) {
            frame(designerService, position);
        }

        assertEquals(200, sentValue(5));

        // the preset selected alone, at the step being edited, then with its steps running
        designerService.setPreviewPreset(true);
        designerService.setSelectedPresetUuid("steps");
        designerService.setSelectedStepUuid("b");

        for (double position = 2000; position < 2500; position += 25, frames++) {
            frame(designerService, position);
        }

        designerService.setStepPreviewRunning(true, 2500);

        for (double position = 2500; position < 5000; position += 25, frames++) {
            frame(designerService, position);
        }

        assertNoDifferences(shadow, frames);
        shadow.close();
    }

    @Test
    void isOffByDefault() throws Exception {
        DefaultDesignerService designerService = designerService(null);
        designerService.load(null, project(), null);
        designerService.setSelectedSceneUuids(List.of("base"));
        frame(designerService, 0.0);

        assertNull(designerService.getShadowEngine());
        assertTrue(sentValue(0) > 0 || sentValue(1) > 0, "The Java engine sends the universe");
    }

    @Test
    void staysOffWithoutEngineWasm() throws Throwable {
        DefaultDesignerService designerService = designerService(true);
        designerService.shadowEngineFactory = audio -> null;
        Project project = project();

        List<String> warnings = DesignerTestEngine.logged(Level.WARN, () -> {
            designerService.load(null, project, null);
            designerService.load(null, project, null);
        });

        designerService.setSelectedSceneUuids(List.of("base"));
        frame(designerService, 0.0);

        assertNull(designerService.getShadowEngine());
        assertEquals(1, warnings.stream().filter(warning -> warning.contains("no shared lighting engine")).count());
        assertTrue(sentValue(0) > 0 || sentValue(1) > 0, "The Java engine sends the universe");
    }

    @Test
    void playsOnWhenTheSharedEngineFails() throws Throwable {
        DefaultDesignerService designerService = designerService(true);
        designerService.shadowEngineFactory = audio -> new ShadowEngine(() -> {
            throw new IllegalStateException("A broken engine");
        }, audio);
        Project project = project();
        List<Integer> sent = new ArrayList<>();

        List<String> errors = DesignerTestEngine.logged(Level.ERROR, () -> {
            designerService.load(null, project, null);
            designerService.setSelectedSceneUuids(List.of("base"));

            for (double position = 0; position < 500; position += 25) {
                frame(designerService, position);
                sent.add(sentValue(0));
            }
        });

        ShadowEngine shadow = designerService.getShadowEngine();
        assertTrue(shadow.isFailed());
        assertEquals(0, shadow.getFramesCompared());
        assertEquals(1, errors.stream().filter(error -> error.contains("A broken engine")).count(), String.join("\n", errors));
        assertEquals(20, sent.size());
        shadow.close();
    }

    @Test
    void comesBackWithTheNextProjectAfterAFault() throws Throwable {
        assumeTrue(LightingEngine.isAvailable(), "No engine.wasm in this build");

        // the first engine fails, the next one plays
        AtomicInteger loads = new AtomicInteger();
        DefaultDesignerService designerService = designerService(true);
        designerService.shadowEngineFactory = audio -> new ShadowEngine(() -> {
            if (loads.getAndIncrement() == 0) {
                throw new IllegalStateException("A fault of the engine");
            }

            return LightingEngine.loadPacked();
        }, audio);
        Project project = project();

        DesignerTestEngine.logged(Level.ERROR, () -> {
            designerService.load(null, project, null);
            designerService.setSelectedSceneUuids(List.of("base"));
            frame(designerService, 0.0);
        });

        ShadowEngine shadow = designerService.getShadowEngine();
        assertTrue(shadow.isFailed());
        assertEquals(0, shadow.getFramesCompared());

        designerService.load(null, project, null);
        designerService.setSelectedSceneUuids(List.of("base"));
        frame(designerService, 25.0);
        frame(designerService, 50.0);

        assertNoDifferences(shadow, 2);
        shadow.close();
    }

}
