package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.message.MessageType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays a sine on every channel of every sound card in the audio settings, through the same output as a composition
 * (mixer, level, volume and sink), and measures it with the level element before the sink. It proves the audio up to
 * the driver, not what comes out of the sound card.
 */
@Service
class AudioSelfTest implements SelfTestStep {

    static final String AUDIO = "audio";

    // Clearly measurable, not too loud on a PA
    static final double LEVEL_DBFS = -20;
    static final double TOLERANCE_DB = 3;
    private static final long START_MILLIS = 5000;

    private final SettingsService settingsService;
    private final AudioService audioService;
    private final CompositionPipelineBuilder compositionPipelineBuilder;
    private final CapabilitiesService capabilitiesService;

    // How long the sine plays. Package visible for the tests.
    long toneMillis = 3000;

    // The pipeline playing at the moment, stopped by restore() at the latest
    private Pipeline pipeline;

    AudioSelfTest(
            SettingsService settingsService,
            AudioService audioService,
            CompositionPipelineBuilder compositionPipelineBuilder,
            CapabilitiesService capabilitiesService,

            // import to make sure, Gstreamer is initialized before using it here
            GstreamerInitService gstreamerInitService
    ) {
        this.settingsService = settingsService;
        this.audioService = audioService;
        this.compositionPipelineBuilder = compositionPipelineBuilder;
        this.capabilitiesService = capabilitiesService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(AUDIO);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            context.report(SelfTestCheck.skipped(AUDIO, null, "GStreamer is not available"));
            return;
        }

        Settings settings = settingsService.getSettings();
        List<AudioDevice> audioDevices = audioService.getAudioDeviceInUseList(settings);

        if (audioDevices.isEmpty()) {
            context.report(SelfTestCheck.skipped(AUDIO, null, "No sound card in the audio settings"));
            return;
        }

        for (AudioDevice audioDevice : audioDevices) {
            int channels = audioService.getChannelCountByAudioDevice(settings, audioDevice);
            String target = audioDevice.getName() + ", " + channels + " channels";

            if (context.isStopped()) {
                context.report(SelfTestCheck.skipped(AUDIO, target, SelfTestContext.STOPPED));
            } else {
                context.report(playTone(audioDevice, channels, target, context));
            }
        }
    }

    private SelfTestCheck playTone(AudioDevice audioDevice, int channels, String target, SelfTestContext context) throws Exception {
        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        List<double[]> peaks = new CopyOnWriteArrayList<>();

        try {
            Pipeline pipeline = buildPipeline(audioDevice, channels);

            Bus bus = pipeline.getBus();
            bus.connect((Bus.ERROR) (source, code, message) -> {
                error.compareAndSet(null, message);
                playing.countDown();
            });
            bus.connect((Bus.STATE_CHANGED) (source, old, current, pending) -> {
                if (source.getTypeName().equals("GstPipeline") && current == State.PLAYING) {
                    playing.countDown();
                }
            });
            bus.connect((Bus.MESSAGE) (bus1, message) -> {
                if (message.getType().equals(MessageType.ELEMENT)) {
                    Structure structure = message.getStructure();
                    if (structure.getName().equals("level")) {
                        peaks.add(structure.getDoubles("peak"));
                    }
                }
            });

            pipeline.play();

            if (!playing.await(START_MILLIS, TimeUnit.MILLISECONDS)) {
                return SelfTestCheck.failed(AUDIO, target, "Did not start playing within " + START_MILLIS / 1000 + " s");
            }

            if (error.get() == null && !context.await(toneMillis)) {
                return SelfTestCheck.skipped(AUDIO, target, SelfTestContext.STOPPED);
            }
        } finally {
            stopPipeline();
        }

        if (error.get() != null) {
            return SelfTestCheck.failed(AUDIO, target, error.get());
        }

        return evaluate(peaks, channels, target);
    }

    private synchronized Pipeline buildPipeline(AudioDevice audioDevice, int channels) {
        pipeline = new Pipeline("selftest_audio");

        Element audioTestSrc = ElementFactory.make("audiotestsrc", "selftest_audiotestsrc");
        audioTestSrc.set("wave", 0 /* sine */);
        audioTestSrc.set("freq", 440.0);
        audioTestSrc.set("volume", Math.pow(10, LEVEL_DBFS / 20));
        pipeline.add(audioTestSrc);

        Element capsFilter = ElementFactory.make("capsfilter", "selftest_capsfilter");
        capsFilter.set("caps", GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=1"));
        pipeline.add(capsFilter);

        // The sine on every channel, with a mix matrix like the audio files of a composition
        Element audioConvert = ElementFactory.make("audioconvert", "selftest_audioconvert");
        setMixMatrix(audioConvert, channels);
        pipeline.add(audioConvert);

        Element audioResample = ElementFactory.make("audioresample", "selftest_audioresample");
        pipeline.add(audioResample);

        Element audioMixer = compositionPipelineBuilder.buildAudioOutputChain(pipeline, audioDevice, true, true, new ArrayList<>());

        audioTestSrc.link(capsFilter);
        capsFilter.link(audioConvert);
        audioConvert.link(audioResample);
        audioResample.link(audioMixer);

        return pipeline;
    }

    private static void setMixMatrix(Element audioConvert, int channels) {
        GValueAPI.GValue mixMatrix = new GValueAPI.GValue();
        GValueAPI.GVALUE_API.g_value_init(mixMatrix, GstApi.GST_API.gst_value_array_get_type());

        for (int i = 0; i < channels; i++) {
            GValueAPI.GValue outputChannel = new GValueAPI.GValue();
            GValueAPI.GVALUE_API.g_value_init(outputChannel, GstApi.GST_API.gst_value_array_get_type());

            GValueAPI.GValue inputChannel = new GValueAPI.GValue(GType.FLOAT);
            inputChannel.setValue(1f);
            GstApi.GST_API.gst_value_array_append_value(outputChannel, inputChannel.getPointer());
            GValueAPI.GVALUE_API.g_value_unset(inputChannel);

            GstApi.GST_API.gst_value_array_append_value(mixMatrix, outputChannel.getPointer());
            GValueAPI.GVALUE_API.g_value_unset(outputChannel);
        }

        GstApi.GST_API.g_object_set_property(audioConvert, "mix-matrix", mixMatrix.getPointer());
        GValueAPI.GVALUE_API.g_value_unset(mixMatrix);
    }

    // Every channel at the level of the sine in every measurement (one per second)
    static SelfTestCheck evaluate(List<double[]> peaks, int channels, String target) {
        if (peaks.isEmpty()) {
            return SelfTestCheck.failed(AUDIO, target, "No level measured");
        }

        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;

        for (double[] peak : peaks) {
            if (peak.length != channels) {
                return SelfTestCheck.failed(AUDIO, target, "Measured " + peak.length + " channels instead of " + channels);
            }

            for (int channel = 0; channel < channels; channel++) {
                if (Math.abs(peak[channel] - LEVEL_DBFS) > TOLERANCE_DB) {
                    return SelfTestCheck.failed(AUDIO, target, "Channel " + (channel + 1) + " at " + format(peak[channel])
                            + " dBFS instead of " + format(LEVEL_DBFS) + " dBFS");
                }

                lowest = Math.min(lowest, peak[channel]);
                highest = Math.max(highest, peak[channel]);
            }
        }

        return SelfTestCheck.passed(AUDIO, target, format(highest) + " to " + format(lowest) + " dBFS on every channel");
    }

    private static String format(double level) {
        return String.format(Locale.ROOT, "%.1f", level);
    }

    private synchronized void stopPipeline() {
        if (pipeline != null) {
            pipeline.stop();
            pipeline.dispose();
            pipeline = null;
        }
    }

    @Override
    public void restore() {
        stopPipeline();
    }

}
