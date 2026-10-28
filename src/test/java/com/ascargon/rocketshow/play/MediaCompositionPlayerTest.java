package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.ActionTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Playing a composition with audio files: the pipeline is simulated and tells the end of the media like GStreamer.
 */
class MediaCompositionPlayerTest {

    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private final DefaultPlayerService playerService = mock(DefaultPlayerService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final CompositionPipeline compositionPipeline = mock(CompositionPipeline.class);

    // The position of the media and when the actions were executed, relative to the start
    private final AtomicLong playStart = new AtomicLong();
    private final AtomicLong mediaMillis = new AtomicLong(300);
    private final List<Long> executedAtMillis = new CopyOnWriteArrayList<>();

    private CompositionPlayer compositionPlayer;

    @BeforeEach
    void setUp() throws Exception {
        Capabilities capabilities = new Capabilities();
        capabilities.setGstreamer(true);
        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        CompositionPipelineBuilder compositionPipelineBuilder = mock(CompositionPipelineBuilder.class);
        when(compositionPipelineBuilder.build(any(), anyLong(), anyBoolean(), any(), any())).thenReturn(compositionPipeline);
        when(compositionPipeline.hasMaster()).thenReturn(true);
        when(compositionPipeline.hasMedia()).thenReturn(true);
        doAnswer(invocation -> {
            playStart.set(System.currentTimeMillis());
            return null;
        }).when(compositionPipeline).playMaster();
        // Like GStreamer: the position of the media, frozen at its end
        when(compositionPipeline.queryMasterPositionMillis()).thenAnswer(invocation -> Math.min(System.currentTimeMillis() - playStart.get(), mediaMillis.get()));

        when(playerService.getNotificationService()).thenReturn(notificationService);
        when(playerService.getCapabilitiesService()).thenReturn(capabilitiesService);
        when(playerService.getSetService()).thenReturn(mock(SetService.class));
        when(playerService.getLightingService()).thenReturn(mock(LightingService.class));
        when(playerService.getDesignerService()).thenReturn(mock(DesignerService.class));
        when(playerService.getActionExecutionService()).thenReturn(actionExecutionService);
        when(playerService.getMidiTimecodeService()).thenReturn(mock(MidiTimecodeService.class));
        when(playerService.getCompositionPipelineBuilder()).thenReturn(compositionPipelineBuilder);

        doAnswer(invocation -> {
            executedAtMillis.add(System.currentTimeMillis() - playStart.get());
            return null;
        }).when(actionExecutionService).executeFromTrigger(any(ActionTrigger.class));

        compositionPlayer = new CompositionPlayer(playerService);
    }

    @AfterEach
    void tearDown() throws Exception {
        compositionPlayer.stop();
    }

    // A song of 300 ms with an action at each passed position
    private static Composition song(long durationMillis, long... actionPositionsMillis) {
        Composition composition = new Composition();
        composition.setName("Song");
        AudioCompositionFile audio = new AudioCompositionFile();
        audio.setName("song.wav");
        composition.getCompositionFileList().add(audio);
        composition.setDurationMillis(durationMillis);

        for (long positionMillis : actionPositionsMillis) {
            ActionTriggerComposition actionTrigger = new ActionTriggerComposition();
            actionTrigger.setPositionMillis(positionMillis);
            composition.getActionTriggerList().add(actionTrigger);
        }

        return composition;
    }

    private void playAndReachTheEndOfTheMedia() throws Exception {
        compositionPlayer.play();
        // The bus of the pipeline reports
        compositionPlayer.onPlaying();
        Thread.sleep(mediaMillis.get());
        compositionPlayer.onEndOfStream();
    }

    @Test
    void endsWithTheMedia() throws Exception {
        compositionPlayer.setComposition(song(300));

        playAndReachTheEndOfTheMedia();

        verify(playerService, timeout(1000)).compositionPlayerFinishedPlaying(compositionPlayer);
    }

    @Test
    void runsTheActionsAfterTheEndOfTheMediaBeforeEnding() throws Exception {
        // E.g. the lights go out a second after the song
        compositionPlayer.setComposition(song(700, 100, 600));

        playAndReachTheEndOfTheMedia();

        verify(playerService, after(200).never()).compositionPlayerFinishedPlaying(compositionPlayer);
        verify(playerService, timeout(1500)).compositionPlayerFinishedPlaying(compositionPlayer);
        assertEquals(2, executedAtMillis.size());
        assertTrue(executedAtMillis.get(1) >= 550 && executedAtMillis.get(1) < 900, "Executed at " + executedAtMillis);
    }

    @Test
    void endsOnceOnARepeatedEndOfTheMedia() throws Exception {
        compositionPlayer.setComposition(song(300));

        playAndReachTheEndOfTheMedia();
        compositionPlayer.onEndOfStream();

        verify(playerService, after(1000).times(1)).compositionPlayerFinishedPlaying(compositionPlayer);
    }

    @Test
    void loopsBackToTheStart() throws Exception {
        Composition song = song(300, 100);
        song.setLoop(true);
        compositionPlayer.setComposition(song);

        playAndReachTheEndOfTheMedia();

        verify(compositionPipeline, timeout(1000)).seek(0);
        assertEquals(CompositionPlayer.PlayState.PLAYING, compositionPlayer.getPlayState());
        verify(playerService, never()).compositionPlayerFinishedPlaying(compositionPlayer);
    }

    @Test
    void stopsOnAnErrorOfThePipeline() throws Exception {
        compositionPlayer.setComposition(song(300));
        compositionPlayer.play();
        compositionPlayer.onPlaying();

        compositionPlayer.onError("Could not open audio device for playback");

        verify(notificationService).notifyClients("Could not open audio device for playback");
        assertEquals(CompositionPlayer.PlayState.STOPPED, compositionPlayer.getPlayState());
        verify(compositionPipeline).stop();
    }

}
