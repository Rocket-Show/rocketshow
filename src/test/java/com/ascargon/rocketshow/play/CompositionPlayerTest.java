package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Project;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Playing a composition without media: its actions at their positions, pausing, seeking, looping and its end. A
 * composition with a designer project but without audio or video plays the same way. Nothing here needs GStreamer.
 */
class CompositionPlayerTest {

    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private final DefaultPlayerService playerService = mock(DefaultPlayerService.class);

    // The positions of the executed actions, in the order they were executed
    private final List<Long> executedPositions = new CopyOnWriteArrayList<>();

    private CompositionPlayer compositionPlayer;

    @BeforeEach
    void setUp() throws Exception {
        // Like the player service, when nothing follows the composition (no set to continue)
        doAnswer(invocation -> {
            invocation.getArgument(0, CompositionPlayer.class).stop();
            return null;
        }).when(playerService).compositionPlayerFinishedPlaying(any());

        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());

        when(playerService.getNotificationService()).thenReturn(mock(NotificationService.class));
        when(playerService.getCapabilitiesService()).thenReturn(capabilitiesService);
        when(playerService.getSetService()).thenReturn(mock(SetService.class));
        when(playerService.getLightingService()).thenReturn(mock(LightingService.class));
        when(playerService.getDesignerService()).thenReturn(mock(DesignerService.class));
        when(playerService.getActionExecutionService()).thenReturn(actionExecutionService);
        when(playerService.getMidiTimecodeService()).thenReturn(mock(MidiTimecodeService.class));
        when(playerService.getCompositionPipelineBuilder()).thenReturn(mock(CompositionPipelineBuilder.class));

        doAnswer(invocation -> {
            executedPositions.add(((ActionTriggerComposition) invocation.getArgument(0)).getPositionMillis());
            return null;
        }).when(actionExecutionService).executeFromTrigger(any(ActionTrigger.class));

        compositionPlayer = new CompositionPlayer(playerService);
    }

    @AfterEach
    void tearDown() throws Exception {
        compositionPlayer.stop();
    }

    // A composition with an action at each passed position, ending at the last one
    private static Composition composition(long... positionsMillis) {
        Composition composition = new Composition();
        composition.setName("Actions");

        for (long positionMillis : positionsMillis) {
            ActionTriggerComposition actionTrigger = new ActionTriggerComposition();
            actionTrigger.setPositionMillis(positionMillis);
            composition.getActionTriggerList().add(actionTrigger);
            composition.setDurationMillis(Math.max(composition.getDurationMillis(), positionMillis));
        }

        return composition;
    }

    private static void waitMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private void waitForState(CompositionPlayer.PlayState playState, long timeoutMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;

        while (compositionPlayer.getPlayState() != playState && System.currentTimeMillis() < end) {
            waitMillis(10);
        }

        assertEquals(playState, compositionPlayer.getPlayState());
    }

    private static int countThreads() {
        return Thread.getAllStackTraces().size();
    }

    @Test
    void executesTheActionsAtTheirPositionsAndStopsAtTheEnd() throws Exception {
        compositionPlayer.setComposition(composition(0, 300, 600));
        long start = System.currentTimeMillis();

        compositionPlayer.play();
        assertEquals(CompositionPlayer.PlayState.PLAYING, compositionPlayer.getPlayState());

        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        long playedMillis = System.currentTimeMillis() - start;

        assertEquals(List.of(0L, 300L, 600L), executedPositions);
        assertTrue(playedMillis >= 600 && playedMillis < 1500, "Played " + playedMillis + " ms");
    }

    @Test
    void stopsThePendingActions() throws Exception {
        compositionPlayer.setComposition(composition(0, 400));

        compositionPlayer.play();
        waitMillis(100);
        compositionPlayer.stop();
        waitMillis(600);

        assertEquals(List.of(0L), executedPositions);
    }

    @Test
    void continuesAfterAPauseWhereItPaused() throws Exception {
        compositionPlayer.setComposition(composition(0, 300, 600));

        compositionPlayer.play();
        waitMillis(150);
        compositionPlayer.pause();
        waitMillis(600);

        // Nothing runs while paused
        assertEquals(List.of(0L), executedPositions);
        long pausedPosition = compositionPlayer.getPositionMillis();
        assertTrue(pausedPosition >= 100 && pausedPosition < 300, "Paused at " + pausedPosition);

        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);

        assertEquals(List.of(0L, 300L, 600L), executedPositions);
    }

    @Test
    void executesTheActionsAfterTheSeekPosition() throws Exception {
        compositionPlayer.setComposition(composition(0, 300, 600, 900));

        compositionPlayer.play();
        waitMillis(100);
        compositionPlayer.seek(500);
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);

        assertEquals(List.of(0L, 600L, 900L), executedPositions);
    }

    @Test
    void endsAtItsDurationAfterSeekingBack() throws Exception {
        compositionPlayer.setComposition(composition(0, 600));

        compositionPlayer.play();
        waitMillis(400);
        long seekTime = System.currentTimeMillis();
        compositionPlayer.seek(0);

        // The whole duration plays again from the start
        waitMillis(350);
        assertEquals(CompositionPlayer.PlayState.PLAYING, compositionPlayer.getPlayState());

        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        long playedAfterSeek = System.currentTimeMillis() - seekTime;
        assertTrue(playedAfterSeek >= 600, "Played " + playedAfterSeek + " ms after seeking back");
        assertEquals(List.of(0L, 0L, 600L), executedPositions);
    }

    @Test
    void executesNoActionsWhenSeekingWhilePaused() throws Exception {
        compositionPlayer.setComposition(composition(0, 300, 600));

        compositionPlayer.play();
        waitMillis(100);
        compositionPlayer.pause();
        compositionPlayer.seek(200);
        waitMillis(700);

        assertEquals(List.of(0L), executedPositions);
        assertEquals(CompositionPlayer.PlayState.PAUSED, compositionPlayer.getPlayState());

        // Playing continues at the seek position
        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        assertEquals(List.of(0L, 300L, 600L), executedPositions);
    }

    @Test
    void executesNoActionsWhenSeekingWhileStopped() throws Exception {
        compositionPlayer.setComposition(composition(0, 300));

        // Never played yet
        compositionPlayer.seek(100);
        waitMillis(400);
        assertEquals(List.of(), executedPositions);

        // Played and stopped before
        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        executedPositions.clear();

        compositionPlayer.seek(100);
        waitMillis(400);
        assertEquals(List.of(), executedPositions);

        // Playing starts at the seek position
        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        assertEquals(List.of(300L), executedPositions);
    }

    @Test
    void ignoresAPauseWhileStopped() throws Exception {
        compositionPlayer.setComposition(composition(0, 300));

        // E.g. a pause trigger pressed while nothing plays
        compositionPlayer.pause();
        assertEquals(CompositionPlayer.PlayState.STOPPED, compositionPlayer.getPlayState());

        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        assertEquals(List.of(0L, 300L), executedPositions);
    }

    @Test
    void playsNothingWithoutMediaActionsOrLight() throws Exception {
        // E.g. a composition with only a lead sheet
        Composition composition = new Composition();
        composition.setName("Lead sheet");
        compositionPlayer.setComposition(composition);

        compositionPlayer.play();

        assertEquals(CompositionPlayer.PlayState.STOPPED, compositionPlayer.getPlayState());
    }

    @Test
    void loopsUntilItIsStopped() throws Exception {
        Composition composition = composition(0, 200);
        composition.setLoop(true);
        compositionPlayer.setComposition(composition);

        compositionPlayer.play();
        waitMillis(700);
        assertEquals(CompositionPlayer.PlayState.PLAYING, compositionPlayer.getPlayState());
        compositionPlayer.stop();

        // At least two runs of both actions
        assertTrue(executedPositions.size() >= 4, "Executed " + executedPositions);
        assertEquals(List.of(0L, 200L, 0L, 200L), executedPositions.subList(0, 4));
    }

    @Test
    void leavesNoThreadsBehind() throws Exception {
        compositionPlayer.setComposition(composition(0, 50, 100));

        // The threads of the first play may stay for good (e.g. timers of the JDK)
        compositionPlayer.play();
        waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        waitMillis(200);
        int threads = countThreads();

        for (int i = 0; i < 10; i++) {
            compositionPlayer.play();
            waitForState(CompositionPlayer.PlayState.STOPPED, 3000);
        }

        waitMillis(500);
        int leakedThreads = countThreads() - threads;
        assertTrue(leakedThreads < 5, leakedThreads + " threads left after 10 plays");
    }

    @Test
    void leavesTheLightsToTheScenesPickedByHandWhenPlayedInBetween() throws Exception {
        DesignerService designerService = playerService.getDesignerService();
        Project project = new Project();
        when(designerService.getProjectByCompositionName("Actions")).thenReturn(project);
        when(designerService.hasManualScenes()).thenReturn(true);
        compositionPlayer.setComposition(composition(0, 1000));

        // The default composition plays in between the others: the scenes picked on the Play screen go before its
        // lights
        compositionPlayer.setDefaultComposition(true);
        compositionPlayer.play();
        verify(designerService, never()).load(any(), any(), any());
        compositionPlayer.stop();

        // A composition played on purpose lights the stage with its own project
        compositionPlayer.setDefaultComposition(false);
        compositionPlayer.play();
        verify(designerService).load(compositionPlayer, project, null);
    }

}
