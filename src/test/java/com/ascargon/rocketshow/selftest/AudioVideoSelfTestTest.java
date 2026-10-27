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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The audio and video checks of the self-test, played with GStreamer: sinks capture what the sound cards and the
 * screen would get.
 */
class AudioVideoSelfTestTest {

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final AudioService audioService = mock(AudioService.class);
    private final HdmiService hdmiService = mock(HdmiService.class);
    private final Capabilities capabilities = new Capabilities();
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);

    private final AudioDevice fourChannels = audioDevice(1, 4);
    private final AudioDevice twoChannels = audioDevice(2, 2);

    // The loudest sample of each channel each sound card got, by the id of the card
    private final Map<Integer, float[]> peaks = new ConcurrentHashMap<>();
    // Sound cards which can't be opened
    private final List<Integer> brokenCards = new java.util.concurrent.CopyOnWriteArrayList<>();

    private AudioSelfTest audioSelfTest;

    @BeforeEach
    void setUp() {
        TestGstreamer.assumeInstalled("audiotestsrc", "audioconvert", "audioresample", "audiomixer", "level", "volume",
                "appsink", "videotestsrc", "videoconvert", "filesink");

        settings.setAudioRate(48000);
        when(settingsService.getSettings()).thenReturn(settings);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of(fourChannels, twoChannels));
        when(audioService.getAudioDeviceAlsaName(any())).thenAnswer(invocation -> invocation.getArgument(0, AudioDevice.class).getKey());
        when(audioService.getChannelCountByAudioDevice(any(), any())).thenAnswer(invocation -> invocation.getArgument(1, AudioDevice.class).getId() == 1 ? 4 : 2);
        when(audioService.getGstAudioSink(any(), anyBoolean())).thenAnswer(invocation -> {
            AudioDevice audioDevice = invocation.getArgument(0);

            if (brokenCards.contains(audioDevice.getId())) {
                Element sink = ElementFactory.make("filesink", "audiosink_" + audioDevice.getKey());
                sink.set("location", "/nonexistent/" + audioDevice.getKey());
                return sink;
            }

            int channels = audioDevice.getId() == 1 ? 4 : 2;
            float[] cardPeaks = new float[channels];
            peaks.put(audioDevice.getId(), cardPeaks);

            AppSink sink = (AppSink) ElementFactory.make("appsink", "audiosink_" + audioDevice.getKey());
            // With the positions of the channels like ALSA tells them (front and rear)
            sink.setCaps(Caps.fromString("audio/x-raw,format=F32LE,layout=interleaved,channel-mask=(bitmask)" + (channels == 4 ? "0x33" : "0x3")));
            sink.set("emit-signals", true);
            sink.connect((AppSink.NEW_SAMPLE) element -> {
                Sample sample = element.pullSample();
                Buffer buffer = sample.getBuffer();
                FloatBuffer samples = buffer.map(false).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                synchronized (cardPeaks) {
                    for (int i = 0; i < samples.remaining(); i++) {
                        cardPeaks[i % channels] = Math.max(cardPeaks[i % channels], Math.abs(samples.get(i)));
                    }
                }
                buffer.unmap();
                sample.dispose();
                return FlowReturn.OK;
            });
            return sink;
        });

        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);
        OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);

        CompositionPipelineBuilder compositionPipelineBuilder = new CompositionPipelineBuilder(settingsService, capabilitiesService,
                mock(ActivityNotificationAudioService.class), mock(ActivityNotificationMidiService.class), operatingSystemInformationService,
                audioService, mock(MidiRouterFactory.class), hdmiService);

        audioSelfTest = new AudioSelfTest(settingsService, audioService, compositionPipelineBuilder, capabilitiesService, mock(GstreamerInitService.class));
        // Long enough for two measurements of the level
        audioSelfTest.toneMillis = 2200;
    }

    private static AudioDevice audioDevice(int id, int channels) {
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.setId(id);
        audioDevice.setKey("card" + id);
        audioDevice.setName("Card " + id);
        return audioDevice;
    }

    private static SelfTestContext context() {
        return new SelfTestContext(new SelfTestRequest(), () -> false);
    }

    @Test
    void playsTheSineOnEveryChannelOfEverySoundCard() throws Exception {
        SelfTestContext context = context();

        audioSelfTest.run(context);

        List<SelfTestCheck> checks = context.getChecks();
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
    }

    @Test
    void aSoundCardWhichCannotBeOpenedFailsAndTheOthersAreStillChecked() throws Exception {
        brokenCards.add(1);
        SelfTestContext context = context();

        audioSelfTest.run(context);

        SelfTestCheck broken = context.getChecks().get(0);
        assertEquals(SelfTestOutcome.FAILED, broken.getResult());
        assertTrue(broken.getDetails().contains("/nonexistent/card1"), broken.getDetails());
        assertEquals(SelfTestOutcome.PASSED, context.getChecks().get(1).getResult(), context.getChecks().get(1).getDetails());
    }

    @Test
    void isSkippedWithoutSoundCardInTheSettings() throws Exception {
        when(audioService.getAudioDeviceInUseList(any())).thenReturn(List.of());
        SelfTestContext context = context();

        audioSelfTest.run(context);

        assertEquals(SelfTestOutcome.SKIPPED, context.getChecks().getFirst().getResult());
        assertEquals("No sound card in the audio settings", context.getChecks().getFirst().getDetails());
    }

    @Test
    void isSkippedWithoutGstreamer() throws Exception {
        capabilities.setGstreamer(false);
        SelfTestContext context = context();

        audioSelfTest.run(context);
        new VideoSelfTest(hdmiService, mock(CompositionPipelineBuilder.class), capabilitiesService, mock(GstreamerInitService.class)).run(context);

        assertEquals(List.of(SelfTestOutcome.SKIPPED, SelfTestOutcome.SKIPPED), context.getChecks().stream().map(SelfTestCheck::getResult).toList());
        assertEquals("GStreamer is not available", context.getChecks().getFirst().getDetails());
    }

    @Test
    void stopsTheSineWhenACompositionStarts() throws Exception {
        long start = System.currentTimeMillis();
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> System.currentTimeMillis() - start > 300);
        audioSelfTest.toneMillis = 10_000;

        audioSelfTest.run(context);

        assertTrue(System.currentTimeMillis() - start < 5000, "Played on");
        assertEquals(List.of(SelfTestOutcome.SKIPPED, SelfTestOutcome.SKIPPED), context.getChecks().stream().map(SelfTestCheck::getResult).toList());
        assertEquals(SelfTestContext.STOPPED, context.getChecks().getFirst().getDetails());
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

    private VideoSelfTest videoSelfTest(Element sink) {
        CompositionPipelineBuilder compositionPipelineBuilder = mock(CompositionPipelineBuilder.class);
        when(compositionPipelineBuilder.createVideoSink()).thenReturn(sink);
        VideoSelfTest videoSelfTest = new VideoSelfTest(hdmiService, compositionPipelineBuilder, capabilitiesService, mock(GstreamerInitService.class));
        videoSelfTest.pictureMillis = 500;
        return videoSelfTest;
    }

    @Test
    void playsATestPictureOnTheScreen() throws Exception {
        when(hdmiService.isDisplayConnected()).thenReturn(true);
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

        SelfTestCheck check = context.getChecks().getFirst();
        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertEquals("Test picture played for 0.5 s on appsink", check.getDetails());
        // 30 frames a second
        assertTrue(frames.get() >= 10, frames.get() + " frames");
    }

    @Test
    void theVideoIsSkippedWithoutDisplay() throws Exception {
        SelfTestContext context = context();

        videoSelfTest(ElementFactory.make("appsink", "videosink")).run(context);

        assertEquals(SelfTestOutcome.SKIPPED, context.getChecks().getFirst().getResult());
        assertEquals("No display connected", context.getChecks().getFirst().getDetails());
    }

    @Test
    void theVideoFailsWhenTheScreenCannotBeOpened() throws Exception {
        when(hdmiService.isDisplayConnected()).thenReturn(true);
        Element sink = ElementFactory.make("filesink", "videosink");
        sink.set("location", "/nonexistent/screen");
        SelfTestContext context = context();

        videoSelfTest(sink).run(context);

        SelfTestCheck check = context.getChecks().getFirst();
        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertTrue(check.getDetails().contains("/nonexistent/screen"), check.getDetails());
    }

}
