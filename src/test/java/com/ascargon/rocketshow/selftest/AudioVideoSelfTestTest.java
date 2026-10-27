package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.audio.ActivityNotificationAudioService;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import com.ascargon.rocketshow.midi.ActivityNotificationMidiService;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import com.ascargon.rocketshow.video.HdmiService;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The audio and video checks of the self-test, played with GStreamer: sinks capture what the sound cards and the
 * screen would get, and a loopback cable plays what a sound card gets back into its inputs.
 */
class AudioVideoSelfTestTest {

    private static final int RATE = 48000;

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final AudioService audioService = mock(AudioService.class);
    private final HdmiService hdmiService = mock(HdmiService.class);
    private final Capabilities capabilities = new Capabilities();
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);

    private final AudioDevice fourChannels = audioDevice(1, "Card 1");
    private final AudioDevice twoChannels = audioDevice(2, "Card 2");

    // The loudest sample of each channel each sound card got, by the id of the card
    private final Map<Integer, float[]> peaks = new ConcurrentHashMap<>();
    // Sound cards which can't be opened
    private final List<Integer> brokenCards = new CopyOnWriteArrayList<>();
    // The loopback cables, by the id of the card: the input each output is connected to (-1 for none)
    private final Map<Integer, int[]> cables = new ConcurrentHashMap<>();
    // The inputs of the sound cards with a cable, while the self-test records them
    private final Map<Integer, AppSrc> inputs = new ConcurrentHashMap<>();

    private AudioSelfTest audioSelfTest;

    @BeforeEach
    void setUp() {
        TestGstreamer.assumeInstalled("audiotestsrc", "audioconvert", "audioresample", "audiomixer", "level", "volume",
                "appsink", "appsrc", "videotestsrc", "videoconvert", "filesink", "uridecodebin", "qtdemux", "h265parse", "libde265dec",
                "fakevideosink");

        settings.setAudioRate(RATE);
        when(settingsService.getSettings()).thenReturn(settings);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of(fourChannels, twoChannels));
        when(audioService.getAudioDeviceAlsaName(any())).thenAnswer(invocation -> invocation.getArgument(0, AudioDevice.class).getKey());
        when(audioService.getChannelCountByAudioDevice(any(), any())).thenAnswer(invocation -> channels(invocation.getArgument(1)));
        when(audioService.getGstAudioSink(any(), anyBoolean())).thenAnswer(invocation -> soundCard(invocation.getArgument(0)));

        // The sound cards themselves, for a unit without audio settings
        when(audioService.getGstHardwareAudioSink(any())).thenAnswer(invocation -> soundCard(invocation.getArgument(0)));
        // Inputs only with a cable
        when(audioService.getHardwareChannelCount(any(), anyBoolean())).thenAnswer(invocation -> {
            AudioDevice audioDevice = invocation.getArgument(0);
            boolean capture = invocation.getArgument(1);
            return !capture || cables.containsKey(audioDevice.getId()) ? channels(audioDevice) : 0;
        });
        when(audioService.getGstHardwareAudioSource(any())).thenAnswer(invocation -> input(invocation.getArgument(0)));

        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        CompositionPipelineBuilder compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, capabilitiesService,
                mock(ActivityNotificationAudioService.class), mock(ActivityNotificationMidiService.class), operatingSystemInformationService,
                audioService, mock(MidiRouterFactory.class), hdmiService);

        audioSelfTest = new AudioSelfTest(settingsService, audioService, compositionPipelineBuilder, capabilitiesService, mock(GstreamerInitService.class));
        // Long enough for two measurements of the level and a second of a loopback
        audioSelfTest.toneMillis = 2200;
    }

    private static AudioDevice audioDevice(int id, String name) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey("card" + id);
        audioDevice.setName(name);
        return audioDevice;
    }

    private static int channels(AudioDevice audioDevice) {
        return audioDevice.getId() == 1 ? 4 : 2;
    }

    // With the positions of the channels like ALSA tells them (front and rear)
    private static String channelMask(int channels) {
        return channels == 4 ? "0x33" : "0x3";
    }

    // What a sound card plays: captured, and played back into its inputs through a loopback cable
    private Element soundCard(AudioDevice audioDevice) {
        if (brokenCards.contains(audioDevice.getId())) {
            Element sink = ElementFactory.make("filesink", "audiosink_" + audioDevice.getKey());
            sink.set("location", "/nonexistent/" + audioDevice.getKey());
            return sink;
        }

        int channels = channels(audioDevice);
        float[] cardPeaks = new float[channels];
        peaks.put(audioDevice.getId(), cardPeaks);

        AppSink sink = (AppSink) ElementFactory.make("appsink", "audiosink_" + audioDevice.getKey());
        sink.setCaps(Caps.fromString("audio/x-raw,format=F32LE,layout=interleaved,rate=" + RATE + ",channel-mask=(bitmask)" + channelMask(channels)));
        sink.set("emit-signals", true);
        sink.connect((AppSink.NEW_SAMPLE) element -> {
            Sample sample = element.pullSample();
            Buffer buffer = sample.getBuffer();
            FloatBuffer samples = buffer.map(false).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            float[] played = new float[samples.remaining()];
            samples.get(played);
            buffer.unmap();
            sample.dispose();

            synchronized (cardPeaks) {
                for (int i = 0; i < played.length; i++) {
                    cardPeaks[i % channels] = Math.max(cardPeaks[i % channels], Math.abs(played[i]));
                }
            }

            playBack(audioDevice, played, channels);
            return FlowReturn.OK;
        });
        return sink;
    }

    private void playBack(AudioDevice audioDevice, float[] played, int channels) {
        AppSrc input = inputs.get(audioDevice.getId());
        int[] cable = cables.get(audioDevice.getId());
        if (input == null || cable == null) {
            return;
        }

        float[] recorded = new float[played.length];
        for (int frame = 0; frame < played.length / channels; frame++) {
            for (int output = 0; output < channels; output++) {
                if (cable[output] >= 0) {
                    recorded[frame * channels + cable[output]] += played[frame * channels + output];
                }
            }
        }

        ByteBuffer bytes = ByteBuffer.allocate(recorded.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(recorded);
        Buffer buffer = new Buffer(recorded.length * 4);
        buffer.map(true).put(bytes);
        buffer.unmap();
        input.pushBuffer(buffer);
    }

    private Element input(AudioDevice audioDevice) {
        int channels = channels(audioDevice);
        AppSrc input = (AppSrc) ElementFactory.make("appsrc", "input_" + audioDevice.getKey());
        input.setCaps(Caps.fromString("audio/x-raw,format=F32LE,layout=interleaved,rate=" + RATE + ",channels=" + channels + ",channel-mask=(bitmask)" + channelMask(channels)));
        input.set("is-live", true);
        input.set("format", 3 /* time */);
        input.set("do-timestamp", true);
        inputs.put(audioDevice.getId(), input);
        return input;
    }

    private static SelfTestContext context() {
        return new SelfTestContext(new SelfTestRequest(), () -> false);
    }

    private static List<SelfTestCheck> checks(SelfTestContext context, String name) {
        return context.getChecks().stream().filter(check -> check.getName().equals(name)).toList();
    }

    private static List<SelfTestOutcome> results(SelfTestContext context, String name) {
        return checks(context, name).stream().map(SelfTestCheck::getResult).toList();
    }

    @Test
    void playsASineOnEveryChannelOfEverySoundCard() throws Exception {
        SelfTestContext context = context();

        audioSelfTest.run(context);

        List<SelfTestCheck> checks = checks(context, AudioSelfTest.AUDIO);
        assertEquals(List.of("Card 1, 4 channels", "Card 2, 2 channels"), checks.stream().map(SelfTestCheck::getTarget).toList());

        for (SelfTestCheck check : checks) {
            assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
            assertTrue(check.getDetails().matches("-(19|20)\\.\\d to -(20|21)\\.\\d dBFS on every channel"), check.getDetails());
        }

        // What the sound cards got: the sine at -20 dBFS
        for (float[] cardPeaks : peaks.values()) {
            for (float peak : cardPeaks) {
                assertEquals(0.1, peak, 0.01);
            }
        }
        assertEquals(4, peaks.get(1).length);

        // Without inputs
        assertEquals(List.of("The sound card has no input", "The sound card has no input"), checks(context, AudioSelfTest.AUDIO_LOOPBACK).stream().map(SelfTestCheck::getDetails).toList());
    }

    @Test
    void aNewUnitPlaysOnEverySoundCardWithAllItsChannels() throws Exception {
        // No sound card in the audio settings
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of());
        AudioDevice usbAudio = audioDevice(1, "USB Audio");
        when(audioService.getAudioDevices()).thenReturn(List.of(usbAudio));
        SelfTestContext context = context();

        audioSelfTest.run(context);

        SelfTestCheck check = checks(context, AudioSelfTest.AUDIO).getFirst();
        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertEquals("USB Audio, 4 channels", check.getTarget());
        for (float peak : peaks.get(1)) {
            assertEquals(0.1, peak, 0.01);
        }
    }

    @Test
    void isSkippedWithoutSoundCard() throws Exception {
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of());
        SelfTestContext context = context();

        audioSelfTest.run(context);

        assertEquals(List.of(SelfTestOutcome.SKIPPED), results(context, AudioSelfTest.AUDIO));
        assertEquals("No sound card", checks(context, AudioSelfTest.AUDIO).getFirst().getDetails());
        assertEquals(List.of(SelfTestOutcome.SKIPPED), results(context, AudioSelfTest.AUDIO_LOOPBACK));
    }

    @Test
    void aSoundCardWhichCannotBeOpenedFailsAndTheOthersAreStillChecked() throws Exception {
        brokenCards.add(1);
        SelfTestContext context = context();

        audioSelfTest.run(context);

        SelfTestCheck broken = checks(context, AudioSelfTest.AUDIO).get(0);
        assertEquals(SelfTestOutcome.FAILED, broken.getResult());
        assertTrue(broken.getDetails().contains("/nonexistent/card1"), broken.getDetails());
        assertEquals(SelfTestOutcome.PASSED, checks(context, AudioSelfTest.AUDIO).get(1).getResult());
    }

    @Test
    void aNewSoundCardWhichCannotBeOpenedFails() throws Exception {
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of());
        when(audioService.getAudioDevices()).thenReturn(List.of(fourChannels));
        doReturn(0).when(audioService).getHardwareChannelCount(any(), eq(false));
        SelfTestContext context = context();

        audioSelfTest.run(context);

        assertEquals("The sound card can't be opened", checks(context, AudioSelfTest.AUDIO).getFirst().getDetails());
    }

    @Test
    void eachInputHearsItsOutputThroughALoopbackCable() throws Exception {
        cables.put(1, new int[]{0, 1, 2, 3});
        cables.put(2, new int[]{0, 1});
        SelfTestContext context = context();

        audioSelfTest.run(context);

        List<SelfTestCheck> loopbacks = checks(context, AudioSelfTest.AUDIO_LOOPBACK);
        assertEquals(List.of(SelfTestOutcome.PASSED, SelfTestOutcome.PASSED), loopbacks.stream().map(SelfTestCheck::getResult).toList(), loopbacks.toString());
        assertTrue(loopbacks.get(0).getDetails().matches("In 1 to 4 hear out 1 to 4 at -(19|20)\\.\\d to -(20|21)\\.\\d dBFS"), loopbacks.get(0).getDetails());
    }

    @Test
    void tellsSwappedChannelsOfALoopbackCable() throws Exception {
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of(twoChannels));
        cables.put(2, new int[]{1, 0});
        SelfTestContext context = context();

        audioSelfTest.run(context);

        SelfTestCheck loopback = checks(context, AudioSelfTest.AUDIO_LOOPBACK).getFirst();
        assertEquals(SelfTestOutcome.FAILED, loopback.getResult());
        assertEquals("In 1 hears out 2, In 2 hears out 1", loopback.getDetails());
    }

    @Test
    void theLoopbackIsSkippedWithoutCable() throws Exception {
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of(twoChannels));
        // An input, but nothing connected to it
        cables.put(2, new int[]{-1, -1});
        SelfTestContext context = context();

        audioSelfTest.run(context);

        SelfTestCheck loopback = checks(context, AudioSelfTest.AUDIO_LOOPBACK).getFirst();
        assertEquals(SelfTestOutcome.SKIPPED, loopback.getResult());
        assertEquals("Nothing received, no loopback cable", loopback.getDetails());
    }

    @Test
    void isSkippedWithoutGstreamer() throws Exception {
        capabilities.setGstreamer(false);
        SelfTestContext context = context();

        audioSelfTest.run(context);
        new VideoSelfTest(hdmiService, mock(CompositionPipelineBuilder.class), capabilitiesService, mock(GstreamerInitService.class)).run(context);

        assertTrue(context.getChecks().stream().allMatch(check -> check.getResult() == SelfTestOutcome.SKIPPED), context.getChecks().toString());
        assertEquals(4, context.getChecks().size());
        assertEquals("GStreamer is not available", context.getChecks().getFirst().getDetails());
    }

    @Test
    void stopsTheSineWhenACompositionStarts() throws Exception {
        long start = System.currentTimeMillis();
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> System.currentTimeMillis() - start > 300);
        audioSelfTest.toneMillis = 10_000;

        audioSelfTest.run(context);

        assertTrue(System.currentTimeMillis() - start < 5000, "Played on");
        assertEquals(List.of(SelfTestOutcome.SKIPPED, SelfTestOutcome.SKIPPED), results(context, AudioSelfTest.AUDIO));
        assertEquals(SelfTestContext.STOPPED, checks(context, AudioSelfTest.AUDIO).getFirst().getDetails());
    }

    @Test
    void failsWhenAChannelIsSilentOrMissing() {
        SelfTestCheck silent = AudioSelfTest.evaluate(List.of(new double[]{-20.0, -20.1}, new double[]{-20.0, -350.0}), 2, "Card");
        assertEquals(SelfTestOutcome.FAILED, silent.getResult());
        assertEquals("Channel 2 at -350.0 dBFS instead of -20.0 dBFS", silent.getDetails());

        SelfTestCheck tooLoud = AudioSelfTest.evaluate(List.<double[]>of(new double[]{-16.5, -20.0}), 2, "Card");
        assertEquals("Channel 1 at -16.5 dBFS instead of -20.0 dBFS", tooLoud.getDetails());

        SelfTestCheck missing = AudioSelfTest.evaluate(List.<double[]>of(new double[]{-20.0, -20.0}), 8, "Card");
        assertEquals("Measured 2 channels instead of 8", missing.getDetails());

        SelfTestCheck nothing = AudioSelfTest.evaluate(List.of(), 2, "Card");
        assertEquals("No level measured", nothing.getDetails());

        SelfTestCheck fine = AudioSelfTest.evaluate(List.<double[]>of(new double[]{-17.5, -22.5}), 2, "Card");
        assertEquals(SelfTestOutcome.PASSED, fine.getResult());
        assertEquals("-17.5 to -22.5 dBFS on every channel", fine.getDetails());
    }

    // Two seconds recorded from the inputs: each input hears the sines of the outputs with the given amplitudes
    private static float[] recording(double[][] amplitudes) {
        int inputs = amplitudes.length;
        float[] samples = new float[2 * AudioSelfTest.LOOPBACK_RATE * inputs];
        for (int frame = 0; frame < samples.length / inputs; frame++) {
            for (int input = 0; input < inputs; input++) {
                double sample = 0;
                for (int output = 0; output < amplitudes[input].length; output++) {
                    sample += amplitudes[input][output] * Math.sin(2 * Math.PI * AudioSelfTest.getFrequency(output) * frame / AudioSelfTest.LOOPBACK_RATE);
                }
                samples[frame * inputs + input] = (float) sample;
            }
        }
        return samples;
    }

    @Test
    void measuresTheOutputsEachInputHears() {
        SelfTestCheck straight = AudioSelfTest.evaluateLoopback(recording(new double[][]{{0.1, 0}, {0, 0.05}}), 2, 2, "Card");
        assertEquals(SelfTestOutcome.PASSED, straight.getResult(), straight.getDetails());
        assertEquals("In 1 to 2 hear out 1 to 2 at -20.0 to -26.0 dBFS", straight.getDetails());

        SelfTestCheck crosstalk = AudioSelfTest.evaluateLoopback(recording(new double[][]{{0.1, 0.02}, {0, 0.1}}), 2, 2, "Card");
        assertEquals(SelfTestOutcome.FAILED, crosstalk.getResult());
        assertEquals("In 1 hears out 2 as well, 14.0 dB less", crosstalk.getDetails());

        SelfTestCheck oneSilent = AudioSelfTest.evaluateLoopback(recording(new double[][]{{0.1, 0}, {0, 0}}), 2, 2, "Card");
        assertEquals("In 2 hears nothing", oneSilent.getDetails());

        SelfTestCheck silent = AudioSelfTest.evaluateLoopback(recording(new double[][]{{0, 0}, {0, 0}}), 2, 2, "Card");
        assertEquals(SelfTestOutcome.SKIPPED, silent.getResult());

        // A stereo input of a sound card with four outputs
        SelfTestCheck stereoInput = AudioSelfTest.evaluateLoopback(recording(new double[][]{{0.1, 0, 0, 0}, {0, 0.1, 0, 0}}), 2, 4, "Card");
        assertEquals("In 1 to 2 hear out 1 to 2 at -20.0 to -20.0 dBFS", stereoInput.getDetails());

        SelfTestCheck tooShort = AudioSelfTest.evaluateLoopback(new float[AudioSelfTest.LOOPBACK_RATE * 2], 2, 2, "Card");
        assertEquals("Recorded only 1000 ms", tooShort.getDetails());
    }

    private VideoSelfTest videoSelfTest(Element sink) {
        CompositionPipelineBuilder compositionPipelineBuilder = mock(CompositionPipelineBuilder.class);
        when(compositionPipelineBuilder.createVideoSink()).thenReturn(sink);
        VideoSelfTest videoSelfTest = new VideoSelfTest(hdmiService, compositionPipelineBuilder, capabilitiesService, mock(GstreamerInitService.class));
        videoSelfTest.pictureMillis = 500;
        return videoSelfTest;
    }

    @Test
    void playsATestPictureOnTheScreen() throws Exception {
        when(hdmiService.getConnectedDisplays()).thenReturn(List.of(new HdmiService.ConnectedDisplay("HDMI-A-1", "1920x1080", "DELL U2415")));
        AtomicLong frames = new AtomicLong();
        AppSink sink = (AppSink) ElementFactory.make("appsink", "videosink");
        sink.set("emit-signals", true);
        sink.connect((AppSink.NEW_SAMPLE) element -> {
            element.pullSample().dispose();
            frames.incrementAndGet();
            return FlowReturn.OK;
        });
        SelfTestContext context = context();

        videoSelfTest(sink).run(context);

        SelfTestCheck check = checks(context, VideoSelfTest.VIDEO).getFirst();
        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertEquals("HDMI-A-1 DELL U2415 1920x1080", check.getTarget());
        assertEquals("Test picture played for 0.5 s on appsink", check.getDetails());
        // 30 frames a second
        assertTrue(frames.get() >= 10, frames.get() + " frames");
    }

    @Test
    void theVideoIsSkippedWithoutDisplay() throws Exception {
        SelfTestContext context = context();

        videoSelfTest(ElementFactory.make("appsink", "videosink")).run(context);

        assertEquals(SelfTestOutcome.SKIPPED, checks(context, VideoSelfTest.VIDEO).getFirst().getResult());
        assertEquals("No display connected", checks(context, VideoSelfTest.VIDEO).getFirst().getDetails());
    }

    @Test
    void theVideoFailsWhenTheScreenCannotBeOpened() throws Exception {
        when(hdmiService.getConnectedDisplays()).thenReturn(List.of(new HdmiService.ConnectedDisplay("HDMI-A-2", null, null)));
        Element sink = ElementFactory.make("filesink", "videosink");
        sink.set("location", "/nonexistent/screen");
        SelfTestContext context = context();

        videoSelfTest(sink).run(context);

        SelfTestCheck check = checks(context, VideoSelfTest.VIDEO).getFirst();
        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("HDMI-A-2", check.getTarget());
        assertTrue(check.getDetails().contains("/nonexistent/screen"), check.getDetails());
    }

    @Test
    void decodesAnH265VideoLikeACompositionAlsoWithoutDisplay() throws Exception {
        SelfTestContext context = context();

        videoSelfTest(ElementFactory.make("appsink", "videosink")).run(context);

        SelfTestCheck check = checks(context, VideoSelfTest.VIDEO_DECODER).getFirst();
        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        // In CI, the software decoder of the bad plugins
        assertTrue(check.getTarget().endsWith("(software)") || check.getTarget().endsWith("(hardware)"), check.getTarget());
        assertEquals("10 frames of H.265 decoded", check.getDetails());
    }

}
