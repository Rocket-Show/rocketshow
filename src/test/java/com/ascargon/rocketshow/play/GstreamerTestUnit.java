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
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A unit playing compositions with GStreamer for the tests: the media is generated here and sinks capture what a
 * sound card or the screen would get. Sound card 1 has four channels (the buses front and rear), sound card 2 has two
 * (the bus monitor).
 */
class GstreamerTestUnit {

    static final int RATE = 48000;

    // The elements the media of the tests need
    static final String[] ELEMENTS = {"uridecodebin", "audiomixer", "level", "wavparse", "midiparse", "appsink",
            "audiotestsrc", "videotestsrc", "jpegenc", "jpegdec", "avimux", "avidemux", "lamemp3enc", "mpg123audiodec",
            "vorbisenc", "vorbisdec", "oggmux", "flacenc", "flacdec", "x265enc", "h265parse", "libde265dec", "matroskamux"};

    final SettingsService settingsService;
    final DefaultPlayerService playerService = mock(DefaultPlayerService.class);
    final NotificationService notificationService = mock(NotificationService.class);
    final ActivityNotificationAudioService activityNotificationAudioService = mock(ActivityNotificationAudioService.class);
    final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    final MidiRouterFactory midiRouterFactory = mock(MidiRouterFactory.class);
    final MidiRouter midiRouter = mock(MidiRouter.class);
    final HdmiService hdmiService = mock(HdmiService.class);

    final AudioDevice fourChannels = audioDevice(1);
    final AudioDevice twoChannels = audioDevice(2);
    final AudioBus front = audioBus("Front", fourChannels);
    final AudioBus rear = audioBus("Rear", fourChannels);
    final AudioBus monitor = audioBus("Monitor", twoChannels);

    // What the sound cards and the screen got, and the MIDI sent, with the time it was sent
    final Map<Integer, Capture> audioCaptures = new ConcurrentHashMap<>();
    final AtomicLong videoFrames = new AtomicLong();
    final List<MidiMessage> midiMessages = new CopyOnWriteArrayList<>();
    final List<Long> midiTimes = new CopyOnWriteArrayList<>();

    // How often a composition ended
    final AtomicInteger finished = new AtomicInteger();

    final CompositionPlayer compositionPlayer;

    GstreamerTestUnit(Path directory) throws Exception {
        settingsService = TestSettings.inDirectory(directory);
        Settings settings = settingsService.getSettings();
        settings.setAudioRate(RATE);
        settings.setEnableMonitor(true);
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

        when(midiRouterFactory.getMidiRouter(any())).thenReturn(midiRouter);
        doAnswer(invocation -> {
            midiMessages.add(invocation.getArgument(0));
            midiTimes.add(System.currentTimeMillis());
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
        when(playerService.getActionExecutionService()).thenReturn(actionExecutionService);
        when(playerService.getMidiTimecodeService()).thenReturn(mock(MidiTimecodeService.class));
        when(playerService.getCompositionPipelineBuilder()).thenReturn(compositionPipelineBuilder);

        // Like the player service, when nothing follows the composition
        doAnswer(invocation -> {
            invocation.getArgument(0, CompositionPlayer.class).stop();
            finished.incrementAndGet();
            return null;
        }).when(playerService).compositionPlayerFinishedPlaying(any());

        compositionPlayer = new CompositionPlayer(playerService);
    }

    private static AudioDevice audioDevice(int id) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey("card" + id);
        audioDevice.setName("Card " + id);
        return audioDevice;
    }

    static AudioBus audioBus(String name, AudioDevice audioDevice) {
        AudioBus audioBus = new AudioBus();
        audioBus.setName(name);
        audioBus.setAudioDevice(audioDevice);
        return audioBus;
    }

    // What a sink got: the frames, the loudest sample of each channel and when it got the first sound
    static class Capture {

        private final int channels;
        private final float[] peaks;
        private long frames;
        private long firstSoundFrame = -1;

        private Capture(int channels) {
            this.channels = channels;
            this.peaks = new float[channels];
        }

        private synchronized void add(ByteBuffer bytes) {
            FloatBuffer samples = bytes.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            int count = samples.remaining();

            for (int i = 0; i < count; i++) {
                float sample = Math.abs(samples.get(i));
                peaks[i % channels] = Math.max(peaks[i % channels], sample);

                if (firstSoundFrame < 0 && sample > 0.05) {
                    firstSoundFrame = frames + i / channels;
                }
            }

            frames += count / channels;
        }

        synchronized float peak(int channel) {
            return peaks[channel];
        }

        // How much it played
        synchronized long millis() {
            return frames * 1000 / RATE;
        }

        // When it played the first sound, or -1
        synchronized long firstSoundMillis() {
            return firstSoundFrame < 0 ? -1 : firstSoundFrame * 1000 / RATE;
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

    Capture card(int id) {
        return audioCaptures.get(id);
    }

    Path mediaFile(String folder, String name) throws Exception {
        Settings settings = settingsService.getSettings();
        Path file = Path.of(settings.getBasePath() + settings.getMediaPath(), folder, name);
        Files.createDirectories(file.getParent());
        return file;
    }

    private AudioCompositionFile audioFile(String name, long millis, AudioBus audioBus) {
        AudioCompositionFile audioCompositionFile = new AudioCompositionFile();
        audioCompositionFile.setName(name);
        audioCompositionFile.setDurationMillis(millis);
        audioCompositionFile.setOutputBusUuid(audioBus.getUuid());
        return audioCompositionFile;
    }

    // A stereo WAV file with a sine of the given amplitudes on the left and the right channel
    AudioCompositionFile wav(String name, long millis, double left, double right, AudioBus audioBus) throws Exception {
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

        return audioFile(name, millis, audioBus);
    }

    // A stereo file with a sine of half the full scale on both channels, encoded by GStreamer (e.g. "lamemp3enc")
    AudioCompositionFile encodedAudio(String name, long millis, String encoder, AudioBus audioBus) throws Exception {
        Path file = mediaFile(settingsService.getSettings().getAudioPath(), name);
        TestGstreamer.encode("audiotestsrc num-buffers=" + millis / 10 + " samplesperbuffer=" + RATE / 100 + " freq=1000 volume=0.5 ! audio/x-raw,rate=" + RATE
                + ",channels=2 ! audioconvert ! " + encoder + " ! filesink location=" + file);
        return audioFile(name, millis, audioBus);
    }

    // A MIDI file with one note: on at the start, off after half a second (480 ticks at 120 bpm)
    MidiCompositionFile midi(String name) throws Exception {
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

    // A video with 25 frames a second, encoded by GStreamer as JPEG or H.265 (like hardware decoders play it)
    VideoCompositionFile video(String name, long millis, boolean h265) throws Exception {
        Path file = mediaFile(settingsService.getSettings().getVideoPath(), name);
        String encoder = h265 ? "x265enc speed-preset=ultrafast ! h265parse ! matroskamux" : "jpegenc ! avimux";
        TestGstreamer.encode("videotestsrc num-buffers=" + millis / 40 + " ! video/x-raw,format=I420,width=64,height=48,framerate=25/1 ! " + encoder + " ! filesink location=" + file);

        VideoCompositionFile videoCompositionFile = new VideoCompositionFile();
        videoCompositionFile.setName(name);
        videoCompositionFile.setDurationMillis(millis);
        return videoCompositionFile;
    }

    static Composition composition(long durationMillis, CompositionFile... files) {
        Composition composition = new Composition();
        composition.setName("Song");
        composition.setDurationMillis(durationMillis);
        composition.getCompositionFileList().addAll(List.of(files));
        return composition;
    }

    // Wait until it held or the time is over
    static boolean waitFor(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }

            Thread.sleep(20);
        }

        return true;
    }

    // Play the composition and wait for its end, returning how long it played
    long playToTheEnd(Composition composition) throws Exception {
        int finishedBefore = finished.get();
        long start = System.currentTimeMillis();
        compositionPlayer.setComposition(composition);
        compositionPlayer.play();
        assertTrue(waitFor(() -> finished.get() > finishedBefore, composition.getDurationMillis() + 5000), "The composition didn't end");
        return System.currentTimeMillis() - start;
    }

    void stop() throws Exception {
        compositionPlayer.stop();
    }

}
