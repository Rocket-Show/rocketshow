package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverse;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The timer of the designer engine sending the universes while a composition plays, the designer previews its project
 * or the scenes picked by hand on the Play screen light the stage: when it starts and stops sending, and what it sends.
 */
class DesignerSendTimerTest {

    private static final String UNIVERSE = "universe";

    // A dimmer at DMX address 1 and the red of a par at address 2. "Bright" plays the dimmer at full over the clip
    // from 20 to 40 seconds into the song, "Red" lights the par.
    private static final String PROJECT = """
            {
              "version": 10,
              "name": "Timer",
              "masterDimmerValue": 1,
              "fixtureProfiles": [
                {
                  "uuid": "test/dimmer",
                  "name": "Dimmer",
                  "availableChannels": {"Dimmer": {"fineChannelAliases": [], "capability": {"type": "Intensity"}}},
                  "modes": [{"name": "1-channel", "shortName": "1ch", "channels": [{"name": "Dimmer"}]}]
                },
                {
                  "uuid": "test/red",
                  "name": "Red",
                  "availableChannels": {"Red": {"fineChannelAliases": [], "capability": {"type": "ColorIntensity", "color": "Red"}}},
                  "modes": [{"name": "1-channel", "shortName": "1ch", "channels": [{"name": "Red"}]}]
                }
              ],
              "fixtures": [
                {"uuid": "dimmer", "name": "Dimmer", "profileUuid": "test/dimmer", "modeShortName": "1ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 0},
                {"uuid": "red", "name": "Red", "profileUuid": "test/red", "modeShortName": "1ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 1}
              ],
              "presetFixtures": [{"fixtureUuid": "dimmer"}, {"fixtureUuid": "red"}],
              "presets": [
                {"uuid": "bright", "name": "Bright", "fixtures": [{"fixtureUuid": "dimmer"}], "effects": [],
                 "steps": [{"uuid": "bright-step", "fixtureCapabilityValues": [{"type": "Intensity", "valuePercentage": 1}]}]},
                {"uuid": "red", "name": "Red", "fixtures": [{"fixtureUuid": "red"}], "effects": [],
                 "steps": [{"uuid": "red-step", "fixtureCapabilityValues": [{"type": "ColorIntensity", "color": "Red", "valuePercentage": 1}]}]}
              ],
              "scenes": [
                {"uuid": "bright-scene", "name": "Bright", "presetUuids": ["bright"]},
                {"uuid": "red-scene", "name": "Red", "presetUuids": ["red"]}
              ],
              "compositions": [{"uuid": "song", "name": "Song", "tracks": [{"uuid": "track", "name": "Track"}],
                                "scenePlaybackRegions": [{"sceneUuid": "bright-scene", "trackUuid": "track", "startMillis": 20000, "endMillis": 40000}]}]
            }
            """;

    @TempDir
    Path basePath;

    // What the engine sent, each time it sent: the values of the universe by DMX address (1 to 512)
    private final List<Map<Integer, Integer>> sent = new CopyOnWriteArrayList<>();

    // The universes registered with the lighting service
    private final List<LightingUniverseState> universes = new CopyOnWriteArrayList<>();

    private LightingService lightingService;

    @Test
    void sendsNothingAfterClosing() throws Exception {
        Files.createDirectories(basePath.resolve("designer"));
        Files.copy(Path.of("dist", "designer_template.json"), basePath.resolve("designer/Template.json"));

        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setEnableMonitor(false);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        FileFilterService fileFilterService = mock(FileFilterService.class);
        when(fileFilterService.filterFile(anyString())).thenReturn(false);

        // A send taking a while (e.g. to OLA), which is running when the composition stops
        LightingService lightingService = mock(LightingService.class);
        AtomicBoolean sending = new AtomicBoolean();
        CountDownLatch sendStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.set(true);
            sendStarted.countDown();
            Thread.sleep(200);
            sending.set(false);
            return null;
        }).when(lightingService).sendExternalSync(anyBoolean());

        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, fileFilterService, lightingService, new DefaultFixturePixelGroupResolveService());
        Project project = designerService.getAllProjects().getFirst();
        for (Fixture fixture : project.getFixtures()) {
            LightingUniverse lightingUniverse = new LightingUniverse();
            lightingUniverse.setUuid(fixture.getDmxUniverseUuid());
            settings.getLightingUniverseList().add(lightingUniverse);
        }

        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        Composition designerComposition = project.getCompositions()[0];
        composition.setName(designerComposition.getName());
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);

        designerService.load(compositionPlayer, project, null);
        designerService.play();
        assertTrue(sendStarted.await(5, TimeUnit.SECONDS));

        designerService.close();

        assertFalse(sending.get(), "Still sending after closing");
    }

    // The engine with the project of this test, sending to a lighting service which keeps what it is sent
    private DefaultDesignerService designerService() throws Exception {
        Files.createDirectories(basePath.resolve("designer"));
        Files.writeString(basePath.resolve("designer/Timer.json"), PROJECT);

        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settings.setDesignerPath("designer");
        settings.setDesignerFrequencyHertz(40);
        settings.setDesignerLivePreview(false);
        settings.setEnableMonitor(false);
        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setUuid(UNIVERSE);
        settings.getLightingUniverseList().add(lightingUniverse);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        lightingService = mock(LightingService.class);
        doAnswer(invocation -> universes.add(invocation.getArgument(0))).when(lightingService).addLightingUniverse(any());
        doAnswer(invocation -> universes.remove((LightingUniverseState) invocation.getArgument(0))).when(lightingService).removeLightingUniverse(any());
        doAnswer(invocation -> {
            Map<Integer, Integer> values = new HashMap<>();
            for (LightingUniverseState universe : universes) {
                universe.getUniverse().forEach((channel, value) -> values.put(channel + 1, value));
            }
            sent.add(values);
            return null;
        }).when(lightingService).sendExternalSync(anyBoolean());

        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), lightingService, new DefaultFixturePixelGroupResolveService());
    }

    // A composition playing the song at the passed position
    private static CompositionPlayer compositionPlayer(long positionMillis) {
        com.ascargon.rocketshow.composition.Composition composition = new com.ascargon.rocketshow.composition.Composition();
        composition.setName("Song");
        CompositionPlayer compositionPlayer = mock(CompositionPlayer.class);
        when(compositionPlayer.getComposition()).thenReturn(composition);
        when(compositionPlayer.getPositionMillis()).thenReturn(positionMillis);
        when(compositionPlayer.getPlayState()).thenReturn(CompositionPlayer.PlayState.PLAYING);
        return compositionPlayer;
    }

    // The values sent next, once the engine sent them
    private Map<Integer, Integer> nextSent() throws InterruptedException {
        int count = sent.size();
        long deadline = System.currentTimeMillis() + 5000;

        while (sent.size() <= count) {
            assertTrue(System.currentTimeMillis() < deadline, "The engine did not send the universe");
            Thread.sleep(5);
        }

        return sent.getLast();
    }

    // Wait for a few periods of the timer (40 Hz): nothing must be sent meanwhile
    private void assertSendsNothingMore() throws InterruptedException {
        int count = sent.size();
        Thread.sleep(200);
        assertEquals(count, sent.size(), "The engine still sends");
    }

    @Test
    void stopsSendingWhilePaused() throws Exception {
        DefaultDesignerService designerService = designerService();
        Project project = designerService.getAllProjects().getFirst();

        designerService.load(compositionPlayer(30000), project, null);
        designerService.play();
        assertEquals(255, nextSent().get(1));
        assertSame(project, designerService.getCurrentProject());

        designerService.pause();
        assertSendsNothingMore();

        designerService.play();
        assertEquals(255, nextSent().get(1));
        designerService.close();
    }

    @Test
    void takesTheLightsOverWhenItLoadsAProjectAndGivesItsUniversesBackWhenItCloses() throws Exception {
        DefaultDesignerService designerService = designerService();
        Project project = designerService.getAllProjects().getFirst();

        designerService.load(compositionPlayer(30000), project, null);
        verify(lightingService).setExternalSync(true);
        assertEquals(1, universes.size());

        designerService.play();
        nextSent();
        designerService.close();

        assertEquals(List.of(), universes);
        assertNull(designerService.getCurrentProject());
        assertSendsNothingMore();
    }

    @Test
    void sendsNothingOfACompositionTakingTheLightsOverBeforeItPlays() throws Exception {
        DefaultDesignerService designerService = designerService();
        Project project = designerService.getAllProjects().getFirst();
        designerService.playManualScenes(project, List.of("red-scene"), 1);
        nextSent();

        // The composition loads its project: the scenes picked by hand wait for it to close
        designerService.load(compositionPlayer(30000), project, null);
        assertSendsNothingMore();

        designerService.play();
        assertEquals(255, nextSent().get(1));

        // ... until it stops
        designerService.close();
        assertTrue(designerService.isPlayingManualScenes());
        designerService.stopManualScenes();
    }

    @Test
    void startsThePreviewAtThePositionOfTheDesigner() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.load(null, designerService.getAllProjects().getFirst(), null);
        designerService.setPreviewComposition("Song");

        // 30 seconds into the song, in the middle of the clip
        designerService.startPreview(30000);
        assertEquals(255, nextSent().get(1));

        designerService.stopPreview();
    }

    @Test
    void stopsThePreviewWhenTheDesignerStopsIt() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.load(null, designerService.getAllProjects().getFirst(), null);
        designerService.setPreviewComposition("Song");
        designerService.startPreview(30000);
        nextSent();

        designerService.stopPreview();

        assertEquals(List.of(), universes);
        assertSendsNothingMore();
    }

    @Test
    void sendsNothingOfAPresetTheDesignerStoppedShowing() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.load(null, designerService.getAllProjects().getFirst(), null);
        designerService.setPreviewPreset(true);
        designerService.setSelectedPresetUuid("red");
        designerService.startPreview(0);
        assertEquals(Map.of(2, 255), nextSent());

        // Nothing is selected anymore: the red is gone, instead of staying on from the frame before
        designerService.setSelectedPresetUuid(null);
        nextSent();
        assertEquals(Map.of(), nextSent());

        designerService.stopPreview();
    }

    @Test
    void picksOtherScenesOfTheProjectPlayingUpWithoutLoadingItAgain() throws Exception {
        DefaultDesignerService designerService = designerService();
        Project project = designerService.getAllProjects().getFirst();
        designerService.playManualScenes(project, List.of("bright-scene"), 1);
        assertEquals(Map.of(1, 255), nextSent());

        designerService.playManualScenes(project, List.of("red-scene"), 1);
        nextSent();
        assertEquals(Map.of(2, 255), nextSent());

        // The same universe all along, the effects run on
        verify(lightingService, times(1)).addLightingUniverse(any());
        designerService.stopManualScenes();
    }

    @Test
    void turnsTheLightsOffWhenTheScenesPickedByHandStop() throws Exception {
        DefaultDesignerService designerService = designerService();
        designerService.playManualScenes(designerService.getAllProjects().getFirst(), List.of("red-scene"), 1);
        nextSent();

        designerService.stopManualScenes();

        assertFalse(designerService.isPlayingManualScenes());
        assertNull(designerService.getCurrentProject());
        assertEquals(List.of(), universes);
        verify(lightingService).setExternalSync(false);
        verify(lightingService).reset();
        assertSendsNothingMore();
    }

}
