package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.audio.AudioAnalysisService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.engine.LightingEngine;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shared lighting engine driving the lights of the designer service (see EngineRenderer): the send timer goes on
 * while the engine takes long for a frame, a fault of the engine is caught and the engine loaded anew, and the pixel
 * effects follow the audio of the buses they name.
 */
class DesignerLightingEngineTest {

    private static final String UNIVERSE = "universe";

    // A dimmer at DMX address 1 lit by the scene "Bright", and a bar of three RGB pixels at address 11, whose pixels a
    // level meter lights as loud as the audio of the bus "bus-a" is (the scene "Meter"). A song plays both.
    private static final String PROJECT = """
            {
              "version": 11,
              "name": "Engine",
              "fixtureProfiles": [
                {"uuid": "dimmer", "name": "Dimmer", "availableChannels": {"Dimmer": {"capability": {"type": "Intensity"}}},
                 "modes": [{"name": "1-channel", "shortName": "1ch", "channels": ["Dimmer"]}]},
                {"uuid": "bar", "name": "Bar", "matrix": {"pixelCount": [3, 1, 1]},
                 "templateChannels": {
                   "Red $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Red"}},
                   "Green $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Green"}},
                   "Blue $pixelKey": {"capability": {"type": "ColorIntensity", "color": "Blue"}}},
                 "modes": [{"name": "9-channel", "shortName": "9ch", "channels": [{"insert": "matrixChannels", "repeatFor": "eachPixelABC",
                   "channelOrder": "perPixel", "templateChannels": ["Red $pixelKey", "Green $pixelKey", "Blue $pixelKey"]}]}]}
              ],
              "fixtures": [
                {"uuid": "dimmer", "name": "Dimmer", "profileUuid": "dimmer", "modeShortName": "1ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 0},
                {"uuid": "bar", "name": "Bar", "profileUuid": "bar", "modeShortName": "9ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 10}
              ],
              "presetFixtures": [{"fixtureUuid": "dimmer"}, {"fixtureUuid": "bar", "pixelKey": "1"}, {"fixtureUuid": "bar", "pixelKey": "2"}, {"fixtureUuid": "bar", "pixelKey": "3"}],
              "presets": [
                {"uuid": "bright", "fixtures": [{"fixtureUuid": "dimmer"}],
                 "steps": [{"uuid": "bright-step", "fixtureCapabilityValues": [{"type": "Intensity", "valuePercentage": 1}]}]},
                {"uuid": "meter", "fixtures": [{"fixtureUuid": "bar", "pixelKey": "1"}, {"fixtureUuid": "bar", "pixelKey": "2"}, {"fixtureUuid": "bar", "pixelKey": "3"}],
                 "steps": [{"uuid": "meter-step"}],
                 "effects": [{"type": "pixel", "uuid": "meter-effect", "pattern": "meter", "audioSource": "composition", "audioBuses": ["bus-a"],
                   "audioFloorDb": -80, "audioAttackMillis": 0, "audioReleaseMillis": 0, "size": 1,
                   "palette": {"stops": [{"position": 0, "color": "#ffffff"}, {"position": 1, "color": "#ffffff"}]}}]}
              ],
              "scenes": [
                {"uuid": "bright-scene", "name": "Bright", "presetUuids": ["bright"]},
                {"uuid": "meter-scene", "name": "Meter", "presetUuids": ["meter"]}
              ],
              "compositions": [{"uuid": "song", "name": "Song", "tracks": [{"uuid": "track", "name": "Track"}],
                                "scenePlaybackRegions": [{"sceneUuid": "bright-scene", "trackUuid": "track", "startMillis": 0, "endMillis": 60000},
                                                         {"sceneUuid": "meter-scene", "trackUuid": "track", "startMillis": 0, "endMillis": 60000}]}]
            }
            """;

    @TempDir
    Path basePath;

    // what was sent, each time: the 512 channels of the universe
    private final List<int[]> sent = new CopyOnWriteArrayList<>();

    private final List<LightingUniverseState> universes = new CopyOnWriteArrayList<>();

    private DefaultDesignerService designerService(Supplier<LightingEngine> engineLoader) throws Exception {
        Files.createDirectories(basePath.resolve("designer"));
        Files.writeString(basePath.resolve("designer/Engine.json"), PROJECT);

        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setUuid(UNIVERSE);
        settings.getLightingUniverseList().add(lightingUniverse);

        for (String bus : List.of("bus-a", "bus-b")) {
            AudioBus audioBus = new AudioBus();
            audioBus.setUuid(bus);
            settings.getAudioBusList().add(audioBus);
        }

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        LightingService lightingService = mock(LightingService.class);
        doAnswer(invocation -> universes.add(invocation.getArgument(0))).when(lightingService).addLightingUniverse(any());
        doAnswer(invocation -> universes.remove((LightingUniverseState) invocation.getArgument(0))).when(lightingService).removeLightingUniverse(any());
        doAnswer(invocation -> {
            int[] values = new int[512];
            universes.getFirst().getUniverse().forEach((channel, value) -> values[channel] = value);
            sent.add(values);
            return null;
        }).when(lightingService).sendExternalSync(anyBoolean());

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), lightingService, new AudioAnalysisService(), engineLoader);

        // the values of the position played, which stands still here
        designerService.renderAhead = false;
        return designerService;
    }

    // a composition playing the song at a position
    private static CompositionPlayer compositionPlayer(long positionMillis) {
        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        composition.setName("Song");
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPositionMillis()).thenReturn(positionMillis);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);
        return compositionPlayer;
    }

    // the values sent next, once sent
    private int[] nextSent() throws InterruptedException {
        int count = sent.size();
        long deadline = System.currentTimeMillis() + 10000;

        while (sent.size() <= count) {
            assertTrue(System.currentTimeMillis() < deadline, "Nothing was sent");
            Thread.sleep(5);
        }

        return sent.getLast();
    }

    // the values sent, once a frame of the passed values was sent
    private int[] sentOnce(java.util.function.Predicate<int[]> values) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;

        while (true) {
            int[] next = nextSent();

            if (values.test(next)) {
                return next;
            }

            assertTrue(System.currentTimeMillis() < deadline, "Not sent: the last values were " + Arrays.toString(Arrays.copyOf(next, 20)));
        }
    }

    // how bright the pixels of the bar are, all channels added
    private static int pixels(int[] values) {
        return Arrays.stream(values, 10, 19).sum();
    }

    @Test
    void sendsOnWhileTheEngineTakesLongForAFrame() throws Exception {
        LightingEngine engine = LightingEngine.loadPacked();
        DefaultDesignerService designerService = designerService(() -> engine);
        designerService.load(compositionPlayer(1000), designerService.getAllProjects().getFirst(), null);
        designerService.play();
        assertEquals(255, sentOnce(values -> values[0] == 255)[0]);

        // the engine is busy (every call of it waits for this lock): the timer doesn't wait for it, it sends what it
        // sent last at its rate
        int before;

        synchronized (engine) {
            nextSent();
            before = sent.size();
            Thread.sleep(500);
            assertTrue(sent.size() - before >= 10, "Sent " + (sent.size() - before) + " times in half a second");
            assertEquals(255, sent.getLast()[0]);
        }

        // and the engine is back with the next frames
        designerService.close();
        assertTrue(designerService.getRenderer() != null);
    }

    @Test
    void loadsTheEngineAnewAfterAFault() throws Throwable {
        // the first engine fails, the next one plays
        AtomicInteger loads = new AtomicInteger();
        DefaultDesignerService designerService = designerService(() -> {
            if (loads.getAndIncrement() == 0) {
                throw new IllegalStateException("A fault of the engine");
            }

            return LightingEngine.loadPacked();
        });

        List<String> errors = DesignerTestEngine.logged(Level.ERROR, () -> {
            designerService.load(compositionPlayer(1000), designerService.getAllProjects().getFirst(), null);
            designerService.play();
            sentOnce(values -> values[0] == 255);
        });

        assertEquals(1, errors.stream().filter(error -> error.contains("is loaded anew")).count(), String.join("\n", errors));
        designerService.close();
    }

    @Test
    void pausesUntilTheNextProjectWhenTheEngineFailsAgain() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        DefaultDesignerService designerService = designerService(() -> {
            loads.incrementAndGet();
            throw new IllegalStateException("A broken engine");
        });
        Project project = designerService.getAllProjects().getFirst();

        List<String> errors = DesignerTestEngine.logged(Level.ERROR, () -> {
            designerService.load(compositionPlayer(1000), project, null);
            designerService.play();
            Thread.sleep(300);
            designerService.getRenderer().awaitIdle();
        });

        // loaded anew once, then it pauses: nothing is sent, the composition plays on
        assertEquals(2, loads.get());
        assertTrue(designerService.getRenderer().isFailed());
        assertEquals(List.of(), sent);
        assertEquals(1, errors.stream().filter(error -> error.contains("pauses until the next project")).count(), String.join("\n", errors));

        // the next project tries again
        designerService.close();
        designerService.load(compositionPlayer(1000), project, null);
        designerService.getRenderer().awaitIdle();
        assertEquals(3, loads.get());
        designerService.close();
    }

    // a frame of the analysis of the audio, as loud as the passed level in dB throughout
    private static double[] frame(double db) {
        double[] frame = new double[AudioAnalysisService.FRAME_LENGTH];
        Arrays.fill(frame, db);
        return frame;
    }

    @Test
    void followsTheAudioOfTheBusesTheEffectsName() throws Exception {
        DefaultDesignerService designerService = designerService(LightingEngine::loadPacked);
        AudioAnalysisService audio = designerService.getAudioAnalysisService();
        Project project = designerService.getAllProjects().getFirst();

        // the composition measures the bus the meter follows
        designerService.load(compositionPlayer(1000), project, null);
        assertEquals(java.util.Set.of("bus-a"), audio.getRequiredBuses());

        // loud on the other bus only: the meter stays dark
        for (int index = 0; index <= 60; index++) {
            audio.setFrame("bus-a", index, frame(AudioAnalysisService.SILENCE_DB));
            audio.setFrame("bus-b", index, frame(0));
        }

        designerService.play();
        int[] silent = sentOnce(values -> values[0] == 255);
        assertEquals(0, pixels(silent));
        designerService.close();

        // loud on its bus: the meter lights the bar
        for (int index = 0; index <= 60; index++) {
            audio.setFrame("bus-a", index, frame(0));
        }

        designerService.load(compositionPlayer(1000), project, null);
        designerService.play();
        assertTrue(pixels(sentOnce(values -> values[0] == 255 && pixels(values) > 0)) > 0);
        designerService.close();
    }

}
