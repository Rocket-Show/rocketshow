package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.midi.ActivityNotificationMidiService;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.midi.MidiRouter;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.HdmiService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The GStreamer pipelines a composition builds, played for real: the media is generated here and sinks capture what
 * a sound card or the screen would get. Needs GStreamer with the base, good and bad plugins (installed in CI),
 * skipped without.
 */
class GstreamerPipelineTest {

    private static final int RATE = 48000;

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private final DefaultPlayerService playerService = mock(DefaultPlayerService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ActivityNotificationAudioService activityNotificationAudioService = mock(ActivityNotificationAudioService.class);
    private final MidiRouter midiRouter = mock(MidiRouter.class);
    private final HdmiService hdmiService = mock(HdmiService.class);

    // A sound card with four channels and one with two
    private final AudioDevice fourChannels = audioDevice(1);
    private final AudioDevice twoChannels = audioDevice(2);
    private AudioBus front;
    private AudioBus rear;
    private AudioBus monitor;

    // What the sound cards and the screen got
    private final Map<Integer, Capture> audioCaptures = new ConcurrentHashMap<>();
    private final AtomicLong videoFrames = new AtomicLong();
    private final List<MidiMessage> midiMessages = new CopyOnWriteArrayList<>();
    private final List<Long> midiMillis = new CopyOnWriteArrayList<>();

    private final CountDownLatch finished = new CountDownLatch(1);
    private CompositionPlayer compositionPlayer;

    @BeforeAll
    static void initGstreamer() {
        TestGstreamer.assumeInstalled("uridecodebin", "audiomixer", "level", "wavparse", "midiparse", "appsink", "videotestsrc", "jpegenc", "jpegdec", "avimux", "avidemux");
    }

    private static AudioDevice audioDevice(int id) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey("card" + id);
        audioDevice.setName("Card " + id);
        return audioDevice;
    }

    private static AudioBus audioBus(String name, AudioDevice audioDevice) {
        AudioBus audioBus = new AudioBus();
        audioBus.setName(name);
        audioBus.setAudioDevice(audioDevice);
        return audioBus;
    }

    // What a sink got: the frames and the loudest sample of each channel
    private static class Capture {

        private final int channels;
        private final float[] peaks;
        private long frames;

        private Capture(int channels) {
            this.channels = channels;
            this.peaks = new float[channels];
        }

        private synchronized void add(ByteBuffer bytes) {
            FloatBuffer samples = bytes.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            int count = samples.remaining();

            for (int i = 0; i < count; i++) {
                peaks[i % channels] = Math.max(peaks[i % channels], Math.abs(samples.get(i)));
            }

            frames += count / channels;
        }

        private synchronized float peak(int channel) {
            return peaks[channel];
        }

        private synchronized long millis() {
            return frames * 1000 / RATE;
        }

    }

    private static void receive(AppSink sink, Consumer<ByteBuffer> receiver) {
        sink.set("emit-signals", true);
        sink.connect((AppSink.NEW_SAMPLE) element -> {
            Sample sample = element.pullSample();
            Buffer buffer = sample.getBuffer();
            receiver.accept(buffer.map(false));
            buffer.unmap();
            sample.dispose();
            return FlowReturn.OK;
        });
    }

    @BeforeEach
    void setUp() throws Exception {
        settingsService = TestSettings.inDirectory(directory);
        Settings settings = settingsService.getSettings();
        settings.setAudioRate(RATE);
        settings.setEnableMonitor(true);
        front = audioBus("Front", fourChannels);
        rear = audioBus("Rear", fourChannels);
        monitor = audioBus("Monitor", twoChannels);
        settings.getAudioBusList().clear();
        settings.getAudioBusList().addAll(List.of(front, rear, monitor));

        Capabilities capabilities = new Capabilities();
        capabilities.setGstreamer(true);
        CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        // Sinks capturing what the sound cards would play
        AudioService audioService = mock(AudioService.class);
        when(audioService.getAudioDeviceAlsaName(any())).thenAnswer(invocation -> invocation.getArgument(0, AudioDevice.class).getKey());
        when(audioService.getChannelCountByAudioDevice(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, AudioDevice.class).getId() == 1 ? 4 : 2);
        when(audioService.getGstAudioSink(any(), anyBoolean())).thenAnswer(invocation -> {
            AudioDevice audioDevice = invocation.getArgument(0);
            Capture capture = audioCaptures.computeIfAbsent(audioDevice.getId(), id -> new Capture(id == 1 ? 4 : 2));
            AppSink sink = (AppSink) ElementFactory.make("appsink", "audiosink_" + audioDevice.getKey());
            // With the positions of the channels like ALSA tells them (front and rear)
            sink.setCaps(Caps.fromString("audio/x-raw,format=F32LE,layout=interleaved,channel-mask=(bitmask)" + (audioDevice.getId() == 1 ? "0x33" : "0x3")));
            receive(sink, capture::add);
            return sink;
        });

        MidiRouterFactory midiRouterFactory = mock(MidiRouterFactory.class);
        when(midiRouterFactory.getMidiRouter(any())).thenReturn(midiRouter);
        AtomicLong playStart = new AtomicLong();
        doAnswer(invocation -> {
            playStart.compareAndSet(0, System.currentTimeMillis());
            midiMessages.add(invocation.getArgument(0));
            midiMillis.add(System.currentTimeMillis() - playStart.get());
            return null;
        }).when(midiRouter).sendSignal(any(), any());

        // A sink watching what the screen would show
        CompositionPipelineBuilder compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, capabilitiesService,
                activityNotificationAudioService, mock(ActivityNotificationMidiService.class), operatingSystemInformationService,
                audioService, midiRouterFactory, hdmiService) {
            @Override
            Element getGstVideoSink() {
                AppSink sink = (AppSink) ElementFactory.make("appsink", "videosink");
                receive(sink, frame -> videoFrames.incrementAndGet());
                return sink;
            }
        };

        when(playerService.getNotificationService()).thenReturn(notificationService);
        when(playerService.getCapabilitiesService()).thenReturn(capabilitiesService);
        when(playerService.getSetService()).thenReturn(mock(SetService.class));
        when(playerService.getLightingService()).thenReturn(mock(LightingService.class));
        when(playerService.getDesignerService()).thenReturn(mock(DesignerService.class));
        when(playerService.getActionExecutionService()).thenReturn(mock(ActionExecutionService.class));
        when(playerService.getMidiTimecodeService()).thenReturn(mock(MidiTimecodeService.class));
        when(playerService.getCompositionPipelineBuilder()).thenReturn(compositionPipelineBuilder);

        // Like the player service, when nothing follows the composition
        doAnswer(invocation -> {
            invocation.getArgument(0, CompositionPlayer.class).stop();
            finished.countDown();
            return null;
        }).when(playerService).compositionPlayerFinishedPlaying(any());

        compositionPlayer = new CompositionPlayer(playerService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (compositionPlayer != null) {
            compositionPlayer.stop();
        }
    }

    private Path mediaFile(String folder, String name) throws Exception {
        Settings settings = settingsService.getSettings();
        Path file = Path.of(settings.getBasePath() + settings.getMediaPath(), folder, name);
        Files.createDirectories(file.getParent());
        return file;
    }

    // A stereo WAV file with a sine of the given amplitudes on the left and the right channel
    private AudioCompositionFile wav(String name, long millis, double left, double right, AudioBus audioBus) throws Exception {
        int frames = (int) (RATE * millis / 1000);
        ByteBuffer bytes = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < frames; i++) {
            double sine = Math.sin(2 * Math.PI * 1000 * i / RATE);
            bytes.putShort((short) Math.round(sine * left * Short.MAX_VALUE));
            bytes.putShort((short) Math.round(sine * right * Short.MAX_VALUE));
        }

        AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes.array()), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, mediaFile(settingsService.getSettings().getAudioPath(), name).toFile());
        }

        AudioCompositionFile audioCompositionFile = new AudioCompositionFile();
        audioCompositionFile.setName(name);
        audioCompositionFile.setDurationMillis(millis);
        audioCompositionFile.setOutputBusUuid(audioBus.getUuid());
        return audioCompositionFile;
    }

    // A MIDI file with one note: on at the start, off after half a second (480 ticks at 120 bpm)
    private MidiCompositionFile midi(String name) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480));
        MidiSystem.write(sequence, 0, mediaFile(settingsService.getSettings().getMidiPath(), name).toFile());

        MidiCompositionFile midiCompositionFile = new MidiCompositionFile();
        midiCompositionFile.setName(name);
        midiCompositionFile.setDurationMillis(500);
        return midiCompositionFile;
    }

    // A video of a second (25 frames), encoded by GStreamer
    private VideoCompositionFile video(String name) throws Exception {
        Path file = mediaFile(settingsService.getSettings().getVideoPath(), name);
        Pipeline pipeline = (Pipeline) Gst.parseLaunch("videotestsrc num-buffers=25 ! video/x-raw,width=64,height=48,framerate=25/1 ! jpegenc ! avimux ! filesink location=" + file);
        CountDownLatch written = new CountDownLatch(1);
        pipeline.getBus().connect((Bus.EOS) source -> written.countDown());
        pipeline.play();
        assertTrue(written.await(10, TimeUnit.SECONDS));
        pipeline.stop();
        pipeline.dispose();

        VideoCompositionFile videoCompositionFile = new VideoCompositionFile();
        videoCompositionFile.setName(name);
        videoCompositionFile.setDurationMillis(1000);
        return videoCompositionFile;
    }

    private static Composition composition(long durationMillis, CompositionFile... files) {
        Composition composition = new Composition();
        composition.setName("Song");
        composition.setDurationMillis(durationMillis);
        composition.getCompositionFileList().addAll(List.of(files));
        return composition;
    }

    private void playToTheEnd(Composition composition) throws Exception {
        compositionPlayer.setComposition(composition);
        compositionPlayer.play();
        assertTrue(finished.await(composition.getDurationMillis() + 5000, TimeUnit.MILLISECONDS), "The composition didn't end");
    }

    private static void assertAbout(double expected, double actual, String what) {
        assertEquals(expected, actual, 0.02, what);
    }

    @Test
    void playsEachFileOnTheChannelsOfItsBus() throws Exception {
        // The front plays on the first two channels of the card, the rear on the other two
        AudioCompositionFile left = wav("left.wav", 1200, 0.8, 0, rear);
        AudioCompositionFile right = wav("right.wav", 1200, 0, 0.8, front);
        right.setVolume(0.5f);
        Composition composition = composition(1200, left, right);
        composition.setAudioVolume(0.5f);

        long start = System.currentTimeMillis();
        playToTheEnd(composition);
        long playedMillis = System.currentTimeMillis() - start;

        Capture card = audioCaptures.get(1);
        assertAbout(0, card.peak(0), "front left");
        assertAbout(0.8 * 0.5 * 0.5, card.peak(1), "front right");
        assertAbout(0.8 * 0.5, card.peak(2), "rear left");
        assertAbout(0, card.peak(3), "rear right");
        assertFalse(audioCaptures.containsKey(2));

        // In real time, all of it
        assertTrue(card.millis() >= 1150 && card.millis() <= 1300, "Played " + card.millis() + " ms");
        assertTrue(playedMillis >= 1150 && playedMillis < 3000, "Ended after " + playedMillis + " ms");

        // The audio monitor of the web app, in dB per channel of the card
        ArgumentCaptor<double[]> levels = ArgumentCaptor.forClass(double[].class);
        verify(activityNotificationAudioService, timeout(1000).atLeastOnce()).notifyClients(levels.capture());
        double[] peaks = levels.getAllValues().getFirst();
        assertEquals(4, peaks.length);
        assertTrue(peaks[0] < -100, "front left " + peaks[0]);
        assertEquals(20 * Math.log10(0.2), peaks[1], 0.5);
        assertEquals(20 * Math.log10(0.4), peaks[2], 0.5);
    }

    @Test
    void loopsAShortFileUntilTheCompositionEnds() throws Exception {
        AudioCompositionFile song = wav("song.wav", 1500, 0.5, 0.5, front);
        AudioCompositionFile click = wav("click.wav", 300, 0.5, 0.5, monitor);
        click.setLoop(true);

        playToTheEnd(composition(1500, song, click));

        // Played in its own pipeline, on its own card
        Capture monitorCard = audioCaptures.get(2);
        assertTrue(monitorCard.millis() >= 1200 && monitorCard.millis() <= 2000, "Looped for " + monitorCard.millis() + " ms");
        assertAbout(0.5, monitorCard.peak(0), "monitor left");
        assertAbout(0.5, audioCaptures.get(1).peak(0), "front left");
    }

    @Test
    void sendsTheNotesOfAMidiFileInTime() throws Exception {
        playToTheEnd(composition(500, midi("song.mid")));

        assertEquals(2, midiMessages.size(), "Sent " + midiMessages.stream().map(message -> HexFormat.of().formatHex(message.getMessage())).toList());
        ShortMessage noteOn = (ShortMessage) midiMessages.get(0);
        ShortMessage noteOff = (ShortMessage) midiMessages.get(1);
        assertEquals(ShortMessage.NOTE_ON, noteOn.getCommand());
        assertEquals(60, noteOn.getData1());
        assertEquals(100, noteOn.getData2());
        assertEquals(ShortMessage.NOTE_OFF, noteOff.getCommand());
        assertTrue(midiMillis.get(1) >= 400 && midiMillis.get(1) <= 700, "Note off after " + midiMillis.get(1) + " ms");
    }

    @Test
    void playsTheVideoWithTheAudio() throws Exception {
        when(hdmiService.isConnected()).thenReturn(true);

        playToTheEnd(composition(1000, video("clip.avi"), wav("song.wav", 1000, 0.5, 0.5, front)));

        assertTrue(videoFrames.get() >= 20, "Showed " + videoFrames.get() + " frames");
        assertTrue(audioCaptures.get(1).millis() >= 900, "Played " + audioCaptures.get(1).millis() + " ms");
    }

    @Test
    void playsNoVideoWithoutAScreen() throws Exception {
        when(hdmiService.isConnected()).thenReturn(false);

        playToTheEnd(composition(1000, video("clip.avi"), wav("song.wav", 1000, 0.5, 0.5, front)));

        assertEquals(0, videoFrames.get());
        assertTrue(audioCaptures.get(1).millis() >= 900, "Played " + audioCaptures.get(1).millis() + " ms");
    }

    @Test
    void pausesAndSeeks() throws Exception {
        compositionPlayer.setComposition(composition(3000, wav("song.wav", 3000, 0.5, 0.5, front)));
        compositionPlayer.play();
        Thread.sleep(500);

        compositionPlayer.pause();
        long pausedAt = compositionPlayer.getPositionMillis();
        assertTrue(pausedAt >= 300 && pausedAt <= 900, "Paused at " + pausedAt);
        long playedWhenPaused = audioCaptures.get(1).millis();
        Thread.sleep(500);
        assertAbout(pausedAt, compositionPlayer.getPositionMillis(), "position while paused");
        // Only what was queued for the card
        assertTrue(audioCaptures.get(1).millis() - playedWhenPaused < 200);

        compositionPlayer.seek(2500);
        compositionPlayer.play();
        long start = System.currentTimeMillis();
        assertTrue(compositionPlayer.getPositionMillis() >= 2500, "At " + compositionPlayer.getPositionMillis());
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        long playedMillis = System.currentTimeMillis() - start;
        assertTrue(playedMillis >= 400 && playedMillis < 1500, "Ended after " + playedMillis + " ms");
    }

    @Test
    void stopsWithAnErrorOnAFileItCannotPlay() throws Exception {
        AudioCompositionFile broken = wav("broken.wav", 1000, 0.5, 0.5, front);
        Files.writeString(mediaFile(settingsService.getSettings().getAudioPath(), "broken.wav"), "this is no audio");

        compositionPlayer.setComposition(composition(1000, broken));
        compositionPlayer.play();

        // GStreamer's reason first, then the error following it
        ArgumentCaptor<String> errors = ArgumentCaptor.forClass(String.class);
        verify(notificationService, timeout(5000).atLeastOnce()).notifyClients(errors.capture());
        assertEquals("Could not determine type of stream.", errors.getAllValues().getFirst());
        long deadline = System.currentTimeMillis() + 5000;
        while (compositionPlayer.getPlayState() != CompositionPlayer.PlayState.STOPPED && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(CompositionPlayer.PlayState.STOPPED, compositionPlayer.getPlayState());
    }

}
