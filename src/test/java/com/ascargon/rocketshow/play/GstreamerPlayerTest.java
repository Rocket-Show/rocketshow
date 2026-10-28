package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.util.ActionTrigger;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ascargon.rocketshow.play.GstreamerTestUnit.composition;
import static com.ascargon.rocketshow.play.GstreamerTestUnit.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Playing compositions with GStreamer like during a show: pausing, seeking, looping, actions after the media and
 * H.265 videos, which hardware decoders can't seek. Needs GStreamer with the base, good and bad plugins (installed in
 * CI), skipped without.
 */
class GstreamerPlayerTest {

    @TempDir
    Path directory;

    private GstreamerTestUnit unit;
    private CompositionPlayer player;

    @BeforeAll
    static void initGstreamer() {
        TestGstreamer.assumeInstalled(GstreamerTestUnit.ELEMENTS);
    }

    @BeforeEach
    void setUp() throws Exception {
        unit = new GstreamerTestUnit(directory);
        player = unit.compositionPlayer;
    }

    @AfterEach
    void tearDown() throws Exception {
        unit.stop();
    }

    @Test
    void pausesAndSeeks() throws Exception {
        player.setComposition(composition(3000, unit.wav("song.wav", 3000, 0.5, 0.5, unit.front)));
        player.play();
        Thread.sleep(500);

        player.pause();
        long pausedAt = player.getPositionMillis();
        assertTrue(pausedAt >= 300 && pausedAt <= 900, "Paused at " + pausedAt);
        long playedWhenPaused = unit.card(1).millis();
        Thread.sleep(500);
        assertEquals(pausedAt, player.getPositionMillis(), 20, "position while paused");
        // Only what was queued for the card
        assertTrue(unit.card(1).millis() - playedWhenPaused < 200);

        player.seek(2500);
        player.play();
        long start = System.currentTimeMillis();
        assertTrue(player.getPositionMillis() >= 2500, "At " + player.getPositionMillis());
        assertTrue(waitFor(() -> unit.finished.get() == 1, 5000));
        long playedMillis = System.currentTimeMillis() - start;
        assertTrue(playedMillis >= 400 && playedMillis < 1500, "Ended after " + playedMillis + " ms");
    }

    @Test
    void continuesWhereItPaused() throws Exception {
        player.setComposition(composition(1000, unit.wav("song.wav", 1000, 0.5, 0.5, unit.front)));
        player.play();
        Thread.sleep(400);
        player.pause();
        Thread.sleep(400);
        player.play();

        assertTrue(waitFor(() -> unit.finished.get() == 1, 5000), "The composition didn't end");

        // All of it once, nothing repeated or left out
        long played = unit.card(1).millis();
        assertTrue(played >= 950 && played <= 1100, "Played " + played + " ms");
    }

    @Test
    void pausesAndSeeksTheLoopingFilesWithTheComposition() throws Exception {
        AudioCompositionFile click = unit.wav("click.wav", 300, 0.5, 0.5, unit.monitor);
        click.setLoop(true);
        player.setComposition(composition(2500, unit.wav("song.wav", 2500, 0.5, 0.5, unit.front), click));
        player.play();
        Thread.sleep(500);

        player.pause();
        long clickedWhenPaused = unit.card(2).millis();
        Thread.sleep(500);
        // Only what was queued for the card
        assertTrue(unit.card(2).millis() - clickedWhenPaused < 200, "Clicked on while paused");

        player.play();
        Thread.sleep(300);
        assertTrue(unit.card(2).millis() > clickedWhenPaused + 100, "Didn't click again");

        // Near the end, the click keeps going until the composition ends
        player.seek(2000);
        long start = System.currentTimeMillis();
        assertTrue(waitFor(() -> unit.finished.get() == 1, 5000), "The composition didn't end");
        long playedMillis = System.currentTimeMillis() - start;
        assertTrue(playedMillis >= 400 && playedMillis < 1500, "Ended after " + playedMillis + " ms");
    }

    @Test
    void loopsTheComposition() throws Exception {
        Composition composition = composition(500, unit.wav("song.wav", 500, 0.5, 0.5, unit.front));
        composition.setLoop(true);
        player.setComposition(composition);

        player.play();
        Thread.sleep(1400);

        assertEquals(CompositionPlayer.PlayState.PLAYING, player.getPlayState());
        long played = unit.card(1).millis();
        assertTrue(played >= 1100, "Played " + played + " ms");
        assertEquals(0, unit.finished.get());
    }

    @Test
    void runsTheActionsAfterTheMediaBeforeEnding() throws Exception {
        // E.g. the lights go out after the song
        List<Long> executedAt = new CopyOnWriteArrayList<>();
        long start = System.currentTimeMillis();
        doAnswer(invocation -> executedAt.add(System.currentTimeMillis() - start)).when(unit.actionExecutionService).executeFromTrigger(any(ActionTrigger.class));
        Composition composition = composition(1000, unit.wav("song.wav", 500, 0.5, 0.5, unit.front));
        ActionTriggerComposition lightsOut = new ActionTriggerComposition();
        lightsOut.setPositionMillis(900);
        composition.getActionTriggerList().add(lightsOut);

        long playedMillis = unit.playToTheEnd(composition);

        assertEquals(1, executedAt.size());
        assertTrue(executedAt.getFirst() >= 850 && executedAt.getFirst() < 1400, "Executed after " + executedAt.getFirst() + " ms");
        assertTrue(playedMillis >= 950 && playedMillis < 2000, "Ended after " + playedMillis + " ms");
    }

    // The designer project playing along with the song has lights until the passed time
    private void lightsUntil(long endMillis) {
        when(unit.designerService.getProjectByCompositionName("Song")).thenReturn(new Project());
        when(unit.designerService.getCompositionEndMillis("Song")).thenReturn(endMillis);
    }

    @Test
    void playsTheLightsAfterTheMediaBeforeEnding() throws Exception {
        // E.g. the lights fade out after the song
        lightsUntil(1000);

        long playedMillis = unit.playToTheEnd(composition(500, unit.wav("song.wav", 500, 0.5, 0.5, unit.front)));

        assertTrue(playedMillis >= 950 && playedMillis < 2000, "Ended after " + playedMillis + " ms");
    }

    @Test
    void loopsAFileAsLongAsTheLightsPlay() throws Exception {
        AudioCompositionFile click = unit.wav("click.wav", 300, 0.5, 0.5, unit.monitor);
        click.setLoop(true);
        lightsUntil(1200);

        long playedMillis = unit.playToTheEnd(composition(300, click));

        assertTrue(playedMillis >= 1150 && playedMillis < 2500, "Ended after " + playedMillis + " ms");
        assertTrue(unit.card(2).millis() >= 900, "Clicked " + unit.card(2).millis() + " ms");
    }

    @Test
    void playsOnWhenALoopingFileCannotBePlayed() throws Exception {
        AudioCompositionFile broken = unit.wav("broken.wav", 300, 0.5, 0.5, unit.monitor);
        broken.setLoop(true);
        Files.writeString(unit.mediaFile(unit.settingsService.getSettings().getAudioPath(), "broken.wav"), "this is no audio");

        long playedMillis = unit.playToTheEnd(composition(1000, unit.wav("song.wav", 1000, 0.5, 0.5, unit.front), broken));

        assertTrue(playedMillis >= 950, "Ended after " + playedMillis + " ms");
        assertTrue(unit.card(1).millis() >= 950, "Played " + unit.card(1).millis() + " ms");
    }

    @Test
    void neitherPausesNorSeeksH265Videos() throws Exception {
        // Hardware decoders of H.265 can't do either
        when(unit.hdmiService.isConnected()).thenReturn(true);
        player.setComposition(composition(2000, unit.video("clip.mkv", 2000, true)));
        player.play();
        assertTrue(waitFor(() -> unit.videoFrames.get() >= 5, 3000), "No video");

        player.pause();
        assertEquals(CompositionPlayer.PlayState.PLAYING, player.getPlayState());

        long before = player.getPositionMillis();
        player.seek(1500);
        assertTrue(player.getPositionMillis() < before + 300, "Seeked from " + before + " to " + player.getPositionMillis());

        assertTrue(waitFor(() -> unit.finished.get() == 1, 5000), "The composition didn't end");
        assertTrue(unit.videoFrames.get() >= 40, "Showed " + unit.videoFrames.get() + " frames");
    }

    @Test
    void loopsAnH265CompositionByStartingItAgain() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);
        Composition composition = composition(600, unit.video("clip.mkv", 600, true));
        composition.setLoop(true);
        player.setComposition(composition);

        player.play();
        Thread.sleep(2000);

        // 15 frames each loop
        assertEquals(CompositionPlayer.PlayState.PLAYING, player.getPlayState());
        assertTrue(unit.videoFrames.get() >= 35, "Showed " + unit.videoFrames.get() + " frames");
        assertEquals(0, unit.finished.get());
    }

    @Test
    void loopsAShortH265VideoByStartingItAgain() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);
        VideoCompositionFile loop = unit.video("loop.mkv", 400, true);
        loop.setLoop(true);

        unit.playToTheEnd(composition(1600, loop, unit.wav("song.wav", 1600, 0.5, 0.5, unit.front)));

        // 10 frames each loop
        assertTrue(unit.videoFrames.get() >= 30, "Showed " + unit.videoFrames.get() + " frames");
        verify(unit.notificationService, never()).notifyClients(any(String.class));
    }

    // Stop at any moment, also while an H.265 video starts again: no crash, and nothing plays on after the stop
    private void stopAtAnyMoment(Composition composition) throws Exception {
        Random random = new Random(1);

        for (int i = 0; i < 12; i++) {
            player.setComposition(composition);
            player.play();
            Thread.sleep(150 + random.nextInt(450));
            player.stop();

            long frames = unit.videoFrames.get();
            Thread.sleep(300);
            assertEquals(frames, unit.videoFrames.get(), "Played on after the stop");
        }
    }

    @Test
    void stopsWhileAShortH265VideoStartsAgain() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);
        VideoCompositionFile loop = unit.video("loop.mkv", 200, true);
        loop.setLoop(true);

        stopAtAnyMoment(composition(2000, loop, unit.wav("song.wav", 2000, 0.5, 0.5, unit.front)));
    }

    @Test
    void stopsWhileAnH265CompositionStartsAgain() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);
        Composition composition = composition(200, unit.video("clip.mkv", 200, true));
        composition.setLoop(true);

        stopAtAnyMoment(composition);
    }

}
