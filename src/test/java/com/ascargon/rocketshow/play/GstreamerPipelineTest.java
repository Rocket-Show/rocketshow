package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.sound.midi.ShortMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import static com.ascargon.rocketshow.play.GstreamerTestUnit.composition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The GStreamer pipelines a composition builds, played for real: the media is generated here and sinks capture what
 * a sound card or the screen would get. Needs GStreamer with the base, good and bad plugins (installed in CI),
 * skipped without.
 */
class GstreamerPipelineTest {

    @TempDir
    Path directory;

    private GstreamerTestUnit unit;

    @BeforeAll
    static void initGstreamer() {
        TestGstreamer.assumeInstalled(GstreamerTestUnit.ELEMENTS);
    }

    @BeforeEach
    void setUp() throws Exception {
        unit = new GstreamerTestUnit(directory);
    }

    @AfterEach
    void tearDown() throws Exception {
        unit.stop();
    }

    private static void assertAbout(double expected, double actual, String what) {
        assertEquals(expected, actual, 0.02, what);
    }

    @Test
    void playsEachFileOnTheChannelsOfItsBus() throws Exception {
        // The front plays on the first two channels of the card, the rear on the other two
        AudioCompositionFile left = unit.wav("left.wav", 1200, 0.8, 0, unit.rear);
        AudioCompositionFile right = unit.wav("right.wav", 1200, 0, 0.8, unit.front);
        right.setVolume(0.5f);
        Composition composition = composition(1200, left, right);
        composition.setAudioVolume(0.5f);

        long playedMillis = unit.playToTheEnd(composition);

        GstreamerTestUnit.Capture card = unit.card(1);
        assertAbout(0, card.peak(0), "front left");
        assertAbout(0.8 * 0.5 * 0.5, card.peak(1), "front right");
        assertAbout(0.8 * 0.5, card.peak(2), "rear left");
        assertAbout(0, card.peak(3), "rear right");
        assertFalse(unit.audioCaptures.containsKey(2));

        // In real time, all of it
        assertTrue(card.millis() >= 1150 && card.millis() <= 1300, "Played " + card.millis() + " ms");
        assertTrue(playedMillis >= 1150 && playedMillis < 3000, "Ended after " + playedMillis + " ms");

        // The audio monitor of the web app, in dB per channel of the card
        ArgumentCaptor<double[]> levels = ArgumentCaptor.forClass(double[].class);
        verify(unit.activityNotificationAudioService, timeout(1000).atLeastOnce()).notifyClients(levels.capture());
        double[] peaks = levels.getAllValues().getFirst();
        assertEquals(4, peaks.length);
        assertTrue(peaks[0] < -100, "front left " + peaks[0]);
        assertEquals(20 * Math.log10(0.2), peaks[1], 0.5);
        assertEquals(20 * Math.log10(0.4), peaks[2], 0.5);
    }

    @Test
    void playsCompressedAudio() throws Exception {
        // The formats bands upload most, each on its own bus
        AudioCompositionFile mp3 = unit.encodedAudio("song.mp3", 1000, "lamemp3enc", unit.front);
        AudioCompositionFile ogg = unit.encodedAudio("song.ogg", 1000, "vorbisenc ! oggmux", unit.rear);
        AudioCompositionFile flac = unit.encodedAudio("song.flac", 1000, "flacenc", unit.monitor);

        unit.playToTheEnd(composition(1000, mp3, ogg, flac));

        for (int channel = 0; channel < 4; channel++) {
            assertEquals(0.5, unit.card(1).peak(channel), 0.05, "channel " + channel);
        }
        assertEquals(0.5, unit.card(2).peak(0), 0.05);
        assertEquals(0.5, unit.card(2).peak(1), 0.05);
        assertTrue(unit.card(1).millis() >= 900, "Played " + unit.card(1).millis() + " ms");
        assertTrue(unit.card(2).millis() >= 900, "Played " + unit.card(2).millis() + " ms");
    }

    @Test
    void leavesOutInactiveFiles() throws Exception {
        AudioCompositionFile song = unit.wav("song.wav", 800, 0.5, 0.5, unit.front);
        AudioCompositionFile muted = unit.wav("muted.wav", 800, 0.5, 0.5, unit.rear);
        muted.setActive(false);

        unit.playToTheEnd(composition(800, song, muted));

        assertAbout(0.5, unit.card(1).peak(0), "front left");
        assertAbout(0, unit.card(1).peak(2), "rear left");
    }

    @Test
    void playsOnTwoSoundCardsAtOnce() throws Exception {
        AudioCompositionFile song = unit.wav("song.wav", 1000, 0.5, 0.5, unit.front);
        AudioCompositionFile click = unit.wav("click.wav", 1000, 0.3, 0.3, unit.monitor);

        unit.playToTheEnd(composition(1000, song, click));

        assertAbout(0.5, unit.card(1).peak(0), "front left");
        assertAbout(0.3, unit.card(2).peak(0), "monitor left");
        assertTrue(unit.card(1).millis() >= 950, "Played " + unit.card(1).millis() + " ms on card 1");
        assertTrue(unit.card(2).millis() >= 950, "Played " + unit.card(2).millis() + " ms on card 2");
    }

    @Test
    void delaysAFileByItsOffset() throws Exception {
        // Adds up with the offset of all audio
        unit.settingsService.getSettings().setOffsetMillisAudio(100);
        AudioCompositionFile song = unit.wav("song.wav", 500, 0.5, 0.5, unit.front);
        song.setOffsetMillis(400);

        unit.playToTheEnd(composition(1000, song));

        long firstSound = unit.card(1).firstSoundMillis();
        assertTrue(firstSound >= 470 && firstSound <= 540, "First sound at " + firstSound + " ms");
    }

    @Test
    void playsNothingOfAFileOnABusWithoutSoundCard() throws Exception {
        AudioBus withoutCard = GstreamerTestUnit.audioBus("Not connected", null);
        unit.settingsService.getSettings().getAudioBusList().add(withoutCard);

        long playedMillis = unit.playToTheEnd(composition(800, unit.wav("song.wav", 800, 0.5, 0.5, withoutCard), unit.wav("click.wav", 800, 0.5, 0.5, unit.front)));

        assertAbout(0.5, unit.card(1).peak(0), "front left");
        assertTrue(playedMillis < 3000, "Ended after " + playedMillis + " ms");
    }

    @Test
    void endsACompositionWithOnlyAFileOnABusWithoutSoundCard() throws Exception {
        // E.g. a unit without the sound card of the other units, playing in sync with them
        AudioBus withoutCard = GstreamerTestUnit.audioBus("Not connected", null);
        unit.settingsService.getSettings().getAudioBusList().add(withoutCard);

        long playedMillis = unit.playToTheEnd(composition(800, unit.wav("song.wav", 800, 0.5, 0.5, withoutCard)));

        assertTrue(playedMillis >= 750 && playedMillis < 3000, "Ended after " + playedMillis + " ms");
    }

    @Test
    void endsACompositionWithOnlyAVideoWithoutAScreen() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(false);

        long playedMillis = unit.playToTheEnd(composition(800, unit.video("clip.avi", 800, false)));

        assertTrue(playedMillis >= 750 && playedMillis < 3000, "Ended after " + playedMillis + " ms");
        assertEquals(0, unit.videoFrames.get());
    }

    @Test
    void loopsAFileAsLongAsAVideoWithoutAScreen() throws Exception {
        // Like on the units with a screen
        when(unit.hdmiService.isConnected()).thenReturn(false);
        AudioCompositionFile click = unit.wav("click.wav", 300, 0.5, 0.5, unit.monitor);
        click.setLoop(true);

        long playedMillis = unit.playToTheEnd(composition(1200, unit.video("clip.avi", 1200, false), click));

        assertTrue(playedMillis >= 1150 && playedMillis < 3000, "Ended after " + playedMillis + " ms");
        assertTrue(unit.card(2).millis() >= 1000, "Looped for " + unit.card(2).millis() + " ms");
    }

    @Test
    void loopsAShortFileUntilTheCompositionEnds() throws Exception {
        AudioCompositionFile song = unit.wav("song.wav", 1500, 0.5, 0.5, unit.front);
        AudioCompositionFile click = unit.wav("click.wav", 300, 0.5, 0.5, unit.monitor);
        click.setLoop(true);

        unit.playToTheEnd(composition(1500, song, click));

        // Played in its own pipeline, on its own card
        GstreamerTestUnit.Capture monitorCard = unit.card(2);
        assertTrue(monitorCard.millis() >= 1200 && monitorCard.millis() <= 2000, "Looped for " + monitorCard.millis() + " ms");
        assertAbout(0.5, monitorCard.peak(0), "monitor left");
        assertAbout(0.5, unit.card(1).peak(0), "front left");
    }

    @Test
    void sendsTheNotesOfAMidiFileInTime() throws Exception {
        long start = System.currentTimeMillis();
        unit.playToTheEnd(composition(500, unit.midi("song.mid")));

        assertEquals(2, unit.midiMessages.size(), "Sent " + unit.midiMessages.stream().map(message -> HexFormat.of().formatHex(message.getMessage())).toList());
        ShortMessage noteOn = (ShortMessage) unit.midiMessages.get(0);
        ShortMessage noteOff = (ShortMessage) unit.midiMessages.get(1);
        assertEquals(ShortMessage.NOTE_ON, noteOn.getCommand());
        assertEquals(60, noteOn.getData1());
        assertEquals(100, noteOn.getData2());
        assertEquals(ShortMessage.NOTE_OFF, noteOff.getCommand());
        long noteMillis = unit.midiTimes.get(1) - unit.midiTimes.get(0);
        assertTrue(noteMillis >= 400 && noteMillis <= 650, "Note off after " + noteMillis + " ms");
        assertTrue(unit.midiTimes.get(0) - start < 500, "Note on after " + (unit.midiTimes.get(0) - start) + " ms");
    }

    @Test
    void delaysTheMidiOfAFileByItsOffset() throws Exception {
        unit.settingsService.getSettings().setOffsetMillisMidi(100);
        MidiCompositionFile midi = unit.midi("song.mid");
        midi.setOffsetMillis(400);
        Composition composition = composition(1200, midi);

        long start = System.currentTimeMillis();
        unit.playToTheEnd(composition);

        long noteOn = unit.midiTimes.get(0) - start;
        assertTrue(noteOn >= 450 && noteOn <= 900, "Note on after " + noteOn + " ms");
    }

    @Test
    void sendsTheMidiOfAFileThroughItsRoutings() throws Exception {
        Settings settings = unit.settingsService.getSettings();
        MidiCompositionFile midi = unit.midi("song.mid");
        MidiRouting routing = new MidiRouting();
        midi.getMidiRoutingList().clear();
        midi.getMidiRoutingList().add(routing);

        unit.playToTheEnd(composition(500, midi));

        verify(unit.midiRouterFactory).getMidiRouter(midi.getMidiRoutingList());
        // The mapping of the routing comes after the one of all MIDI
        MidiMapping compositionMapping = routing.getMidiMapping().getParent();
        assertSame(settings.getMidiMapping(), compositionMapping.getParent());
        // Closed with the composition
        verify(unit.midiRouter).close();
    }

    @Test
    void showsTheVideoWithTheAudio() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);

        unit.playToTheEnd(composition(1000, unit.video("clip.avi", 1000, false), unit.wav("song.wav", 1000, 0.5, 0.5, unit.front)));

        assertTrue(unit.videoFrames.get() >= 20, "Showed " + unit.videoFrames.get() + " frames");
        assertTrue(unit.card(1).millis() >= 900, "Played " + unit.card(1).millis() + " ms");
    }

    @Test
    void loopsAShortVideoUntilTheCompositionEnds() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(true);
        VideoCompositionFile loop = unit.video("loop.avi", 400, false);
        loop.setLoop(true);

        unit.playToTheEnd(composition(1200, loop, unit.wav("song.wav", 1200, 0.5, 0.5, unit.front)));

        // 10 frames each loop
        assertTrue(unit.videoFrames.get() >= 25, "Showed " + unit.videoFrames.get() + " frames");
    }

    @Test
    void showsNoVideoWithoutAScreen() throws Exception {
        when(unit.hdmiService.isConnected()).thenReturn(false);

        unit.playToTheEnd(composition(1000, unit.video("clip.avi", 1000, false), unit.wav("song.wav", 1000, 0.5, 0.5, unit.front)));

        assertEquals(0, unit.videoFrames.get());
        assertTrue(unit.card(1).millis() >= 900, "Played " + unit.card(1).millis() + " ms");
    }

    @Test
    void sendsNoLevelsWithoutTheMonitor() throws Exception {
        unit.settingsService.getSettings().setEnableMonitor(false);

        unit.playToTheEnd(composition(1200, unit.wav("song.wav", 1200, 0.5, 0.5, unit.front)));

        verify(unit.activityNotificationAudioService, after(300).never()).notifyClients(any(double[].class));
    }

    @Test
    void sendsNoLevelsOfSamples() throws Exception {
        unit.compositionPlayer.setSample(true);

        unit.playToTheEnd(composition(1200, unit.wav("sample.wav", 1200, 0.5, 0.5, unit.front)));

        assertAbout(0.5, unit.card(1).peak(0), "front left");
        verify(unit.activityNotificationAudioService, after(300).never()).notifyClients(any(double[].class));
    }

    @Test
    void stopsWithAnErrorOnAFileItCannotPlay() throws Exception {
        AudioCompositionFile broken = unit.wav("broken.wav", 1000, 0.5, 0.5, unit.front);
        Files.writeString(unit.mediaFile(unit.settingsService.getSettings().getAudioPath(), "broken.wav"), "this is no audio");

        unit.compositionPlayer.setComposition(composition(1000, broken));
        unit.compositionPlayer.play();

        // GStreamer's reason first, then the error following it, with the file and the element which failed
        ArgumentCaptor<String> errors = ArgumentCaptor.forClass(String.class);
        verify(unit.notificationService, timeout(5000).atLeastOnce()).notifyClients(errors.capture());
        assertEquals("broken.wav: typefind: Could not determine type of stream.", errors.getAllValues().getFirst());
        assertTrue(GstreamerTestUnit.waitFor(() -> unit.compositionPlayer.getPlayState() == CompositionPlayer.PlayState.STOPPED, 5000));
        verify(unit.playerService, never()).compositionPlayerFinishedPlaying(any());
        assertEquals(List.of(), unit.midiMessages);
    }

    @Test
    void tellsWhyItCouldNotPlayAFileWithOtherChannelsThanItKnows() throws Exception {
        // E.g. replaced by a file with other channels: the mix matrix still takes 6
        AudioCompositionFile song = unit.wav("song.wav", 1000, 0.5, 0.5, unit.front);
        song.setChannels(6);

        unit.compositionPlayer.setComposition(composition(1000, song));
        unit.compositionPlayer.play();

        // GStreamer only tells that nothing took the stream of the file, the reason follows
        ArgumentCaptor<String> errors = ArgumentCaptor.forClass(String.class);
        verify(unit.notificationService, timeout(5000).atLeastOnce()).notifyClients(errors.capture());
        String[] error = errors.getAllValues().getFirst().split("\n");
        assertEquals(2, error.length);
        assertTrue(error[0].matches("song\\.wav: wavparse\\d+: Internal data stream error\\. \\(streaming stopped, reason not-linked \\(-1\\)\\)"), error[0]);
        assertTrue(error[1].startsWith("Could not link audiouridecodebin0:src_0 -> audioconvert0:sink (mismatch in channels). Offered: audio/x-raw, "), error[1]);
        assertTrue(GstreamerTestUnit.waitFor(() -> unit.compositionPlayer.getPlayState() == CompositionPlayer.PlayState.STOPPED, 5000));
    }

    @Test
    void tellsTheErrorOfALoopingFileAndPlaysOn() throws Exception {
        AudioCompositionFile song = unit.wav("song.wav", 1500, 0.5, 0.5, unit.front);
        AudioCompositionFile click = unit.wav("click.wav", 300, 0.5, 0.5, unit.monitor);
        click.setLoop(true);
        Files.writeString(unit.mediaFile(unit.settingsService.getSettings().getAudioPath(), "click.wav"), "this is no audio");

        unit.playToTheEnd(composition(1500, song, click));

        // Told with its file, while the song played to its end
        ArgumentCaptor<String> errors = ArgumentCaptor.forClass(String.class);
        verify(unit.notificationService, atLeastOnce()).notifyClients(errors.capture());
        assertEquals("click.wav: typefind: Could not determine type of stream.", errors.getAllValues().getFirst());
        assertAbout(0.5, unit.card(1).peak(0), "front left");
        verify(unit.playerService).compositionPlayerFinishedPlaying(any());
    }

}
