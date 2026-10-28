package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.audio.AudioService;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.message.MessageType;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays a sine on every channel of every sound card in the audio settings (a new unit without them: of every sound
 * card), each channel at its own frequency, through the same output as a composition (mixer, level, volume and sink).
 * It measures them with the level element before the sink, which proves the audio up to the driver. With a cable
 * from the outputs to the inputs of the sound card, it records them as well, which proves the analog path and tells
 * swapped channels.
 */
@Service
class AudioSelfTest implements SelfTestStep {

    static final String AUDIO = "audio";
    static final String AUDIO_LOOPBACK = "audio-loopback";

    // Clearly measurable, not too loud on a PA
    static final double LEVEL_DBFS = -20;
    static final double TOLERANCE_DB = 3;

    // An input of a loopback hears its output at least this loud, and the other outputs this much less
    static final double LOOPBACK_MIN_DBFS = -60;
    static final double LOOPBACK_SEPARATION_DB = 20;

    // The loopback is recorded at this rate and analyzed over one second of it, which holds every frequency in whole
    // periods
    static final int LOOPBACK_RATE = 48000;

    private static final long START_MILLIS = 5000;

    private final SettingsService settingsService;
    private final AudioService audioService;
    private final CompositionPipelineBuilder compositionPipelineBuilder;
    private final CapabilitiesService capabilitiesService;

    // How long the sine plays. Package visible for the tests.
    long toneMillis = 3000;

    // The pipelines playing and recording at the moment, stopped by restore() at the latest
    private Pipeline pipeline;
    private Pipeline loopbackPipeline;

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
        return List.of(AUDIO, AUDIO_LOOPBACK);
    }

    // The frequency of each channel. None is the 2nd or 3rd harmonic of another, so a loopback tells them apart.
    static double getFrequency(int channel) {
        return 400 + 150 * channel;
    }

    private static void skipBoth(SelfTestContext context, String target, String reason) {
        context.report(SelfTestCheck.skipped(AUDIO, target, reason));
        context.report(SelfTestCheck.skipped(AUDIO_LOOPBACK, target, reason));
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            skipBoth(context, null, "GStreamer is not available");
            return;
        }

        Settings settings = settingsService.getSettings();
        List<AudioDevice> audioDevices = audioService.getAudioDeviceInUseList(settings);

        // E.g. a new unit, which has no sound card in its settings yet: every sound card, with all its channels
        boolean ofTheSettings = !audioDevices.isEmpty();
        if (!ofTheSettings) {
            audioDevices = audioService.getAudioDevices();
        }

        if (audioDevices.isEmpty()) {
            skipBoth(context, null, "No sound card");
            return;
        }

        for (AudioDevice audioDevice : audioDevices) {
            int channels = ofTheSettings ? audioService.getChannelCountByAudioDevice(settings, audioDevice) : audioService.getHardwareChannelCount(audioDevice, false);
            String target = audioDevice.getName() + ", " + channels + " channels";

            if (context.isStopped()) {
                skipBoth(context, target, SelfTestContext.STOPPED);
            } else if (channels == 0) {
                context.report(SelfTestCheck.failed(AUDIO, target, "The sound card can't be opened"));
                context.report(SelfTestCheck.skipped(AUDIO_LOOPBACK, target, "The sound card can't be opened"));
            } else {
                playTone(audioDevice, channels, ofTheSettings, target, context);
            }
        }
    }

    private void playTone(AudioDevice audioDevice, int channels, boolean ofTheSettings, String target, SelfTestContext context) throws Exception {
        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        List<double[]> peaks = new CopyOnWriteArrayList<>();

        int inputChannels = audioService.getHardwareChannelCount(audioDevice, true);
        Recording recording = null;

        try {
            // Listen from the start
            if (inputChannels > 0) {
                recording = startRecording(audioDevice, inputChannels);
            }

            Pipeline pipeline = buildPipeline(audioDevice, channels, ofTheSettings);

            Bus bus = pipeline.getBus();
            SelfTestPipelineErrors.onError(bus, message -> {
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
                context.report(SelfTestCheck.failed(AUDIO, target, "Did not start playing within " + START_MILLIS / 1000 + " s"));
                context.report(SelfTestCheck.skipped(AUDIO_LOOPBACK, target, "The sound card did not play"));
                return;
            }

            if (error.get() == null && !context.await(toneMillis)) {
                skipBoth(context, target, SelfTestContext.STOPPED);
                return;
            }
        } finally {
            stopPipelines();
        }

        if (error.get() != null) {
            context.report(SelfTestCheck.failed(AUDIO, target, error.get()));
            context.report(SelfTestCheck.skipped(AUDIO_LOOPBACK, target, "The sound card did not play"));
            return;
        }

        context.report(evaluate(peaks, channels, target));

        if (recording == null) {
            context.report(SelfTestCheck.skipped(AUDIO_LOOPBACK, target, "The sound card has no input"));
        } else if (recording.error.get() != null) {
            context.report(SelfTestCheck.failed(AUDIO_LOOPBACK, target, "Could not record: " + recording.error.get()));
        } else {
            context.report(evaluateLoopback(recording.getSamples(), recording.channels.get(), channels, target));
        }
    }

    private synchronized Pipeline buildPipeline(AudioDevice audioDevice, int channels, boolean ofTheSettings) {
        pipeline = new Pipeline("selftest_audio");

        // The mixer of the output of a composition, or of the sound card itself
        Element audioMixer = ofTheSettings
                ? compositionPipelineBuilder.buildAudioOutputChain(pipeline, audioDevice, true, true, new ArrayList<>())
                : buildHardwareOutputChain(pipeline, audioDevice, channels);

        // A sine on each channel, with a mix matrix like the audio files of a composition
        for (int channel = 0; channel < channels; channel++) {
            Element audioTestSrc = ElementFactory.make("audiotestsrc", "selftest_audiotestsrc" + channel);
            audioTestSrc.set("wave", 0 /* sine */);
            audioTestSrc.set("freq", getFrequency(channel));
            audioTestSrc.set("volume", Math.pow(10, LEVEL_DBFS / 20));
            pipeline.add(audioTestSrc);

            Element capsFilter = ElementFactory.make("capsfilter", "selftest_capsfilter" + channel);
            capsFilter.set("caps", GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=1"));
            pipeline.add(capsFilter);

            Element audioConvert = ElementFactory.make("audioconvert", "selftest_audioconvert" + channel);
            setMixMatrix(audioConvert, channels, channel);
            pipeline.add(audioConvert);

            Element audioResample = ElementFactory.make("audioresample", "selftest_audioresample" + channel);
            pipeline.add(audioResample);

            audioTestSrc.link(capsFilter);
            capsFilter.link(audioConvert);
            audioConvert.link(audioResample);
            audioResample.link(audioMixer);
        }

        return pipeline;
    }

    // Like the output of a composition, on the sound card itself instead of the device of the audio settings
    private Element buildHardwareOutputChain(Pipeline target, AudioDevice audioDevice, int channels) {
        Element audioMixer = ElementFactory.make("audiomixer", "selftest_audiomixer");
        target.add(audioMixer);

        Element capsFilter = ElementFactory.make("capsfilter", "selftest_outputcapsfilter");
        capsFilter.set("caps", GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=" + channels));
        target.add(capsFilter);

        Element level = ElementFactory.make("level", "selftest_level");
        level.set("interval", 1000 * 1000000);
        level.set("post-messages", true);
        target.add(level);

        Element queue = ElementFactory.make("queue", "selftest_queue");
        target.add(queue);

        Element audioConvert = ElementFactory.make("audioconvert", "selftest_outputaudioconvert");
        target.add(audioConvert);

        Element sink = audioService.getGstHardwareAudioSink(audioDevice);
        target.add(sink);

        audioMixer.link(capsFilter);
        capsFilter.link(level);
        level.link(queue);
        queue.link(audioConvert);
        audioConvert.link(sink);

        return audioMixer;
    }

    // Only the input on the passed output channel
    private static void setMixMatrix(Element audioConvert, int channels, int channel) {
        GValueAPI.GValue mixMatrix = new GValueAPI.GValue();
        GValueAPI.GVALUE_API.g_value_init(mixMatrix, GstApi.GST_API.gst_value_array_get_type());

        for (int i = 0; i < channels; i++) {
            GValueAPI.GValue outputChannel = new GValueAPI.GValue();
            GValueAPI.GVALUE_API.g_value_init(outputChannel, GstApi.GST_API.gst_value_array_get_type());

            GValueAPI.GValue inputChannel = new GValueAPI.GValue(GType.FLOAT);
            inputChannel.setValue(i == channel ? 1f : 0f);
            GstApi.GST_API.gst_value_array_append_value(outputChannel, inputChannel.getPointer());
            GValueAPI.GVALUE_API.g_value_unset(inputChannel);

            GstApi.GST_API.gst_value_array_append_value(mixMatrix, outputChannel.getPointer());
            GValueAPI.GVALUE_API.g_value_unset(outputChannel);
        }

        GstApi.GST_API.g_object_set_property(audioConvert, "mix-matrix", mixMatrix.getPointer());
        GValueAPI.GVALUE_API.g_value_unset(mixMatrix);
    }

    // The samples recorded from the inputs of a sound card, interleaved
    private static class Recording {

        private final AtomicInteger channels = new AtomicInteger();
        private final AtomicReference<String> error = new AtomicReference<>();
        private final List<float[]> buffers = new CopyOnWriteArrayList<>();

        float[] getSamples() {
            int count = buffers.stream().mapToInt(buffer -> buffer.length).sum();
            float[] samples = new float[count];
            int position = 0;
            for (float[] buffer : buffers) {
                System.arraycopy(buffer, 0, samples, position, buffer.length);
                position += buffer.length;
            }
            return samples;
        }

    }

    private synchronized Recording startRecording(AudioDevice audioDevice, int inputChannels) throws InterruptedException {
        Recording recording = new Recording();
        CountDownLatch recordingStarted = new CountDownLatch(1);

        loopbackPipeline = new Pipeline("selftest_loopback");

        Element source = audioService.getGstHardwareAudioSource(audioDevice);
        loopbackPipeline.add(source);

        // Every input, unchanged
        Element channelsFilter = ElementFactory.make("capsfilter", "selftest_loopbackchannels");
        channelsFilter.set("caps", GstApi.GST_API.gst_caps_from_string("audio/x-raw,channels=" + inputChannels));
        loopbackPipeline.add(channelsFilter);

        Element audioConvert = ElementFactory.make("audioconvert", "selftest_loopbackaudioconvert");
        loopbackPipeline.add(audioConvert);

        Element audioResample = ElementFactory.make("audioresample", "selftest_loopbackaudioresample");
        loopbackPipeline.add(audioResample);

        Element formatFilter = ElementFactory.make("capsfilter", "selftest_loopbackformat");
        formatFilter.set("caps", GstApi.GST_API.gst_caps_from_string("audio/x-raw,format=F32LE,layout=interleaved,rate=" + LOOPBACK_RATE));
        loopbackPipeline.add(formatFilter);

        AppSink sink = (AppSink) ElementFactory.make("appsink", "selftest_loopbacksink");
        sink.set("emit-signals", true);
        sink.set("sync", false);
        // Playing without waiting for the first samples, which come with the sine
        sink.set("async", false);
        sink.connect((AppSink.NEW_SAMPLE) element -> {
            Sample sample = element.pullSample();
            recording.channels.compareAndSet(0, sample.getCaps().getStructure(0).getInteger("channels"));
            Buffer buffer = sample.getBuffer();
            FloatBuffer floats = buffer.map(false).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            float[] samples = new float[floats.remaining()];
            floats.get(samples);
            recording.buffers.add(samples);
            buffer.unmap();
            sample.dispose();
            return FlowReturn.OK;
        });
        loopbackPipeline.add(sink);

        source.link(channelsFilter);
        channelsFilter.link(audioConvert);
        audioConvert.link(audioResample);
        audioResample.link(formatFilter);
        formatFilter.link(sink);

        Bus bus = loopbackPipeline.getBus();
        SelfTestPipelineErrors.onError(bus, message -> {
            recording.error.compareAndSet(null, message);
            recordingStarted.countDown();
        });
        bus.connect((Bus.STATE_CHANGED) (stateSource, old, current, pending) -> {
            if (stateSource.getTypeName().equals("GstPipeline") && current == State.PLAYING) {
                recordingStarted.countDown();
            }
        });

        loopbackPipeline.play();
        if (!recordingStarted.await(START_MILLIS, TimeUnit.MILLISECONDS)) {
            recording.error.compareAndSet(null, "Did not start recording within " + START_MILLIS / 1000 + " s");
        }

        return recording;
    }

    // Every channel at the level of its sine in every measurement (one per second)
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

    // The level of a frequency on a channel of interleaved samples (Goertzel), in dBFS
    static double getLevel(float[] samples, int channels, int channel, int start, int count, double frequency) {
        double coefficient = 2 * Math.cos(2 * Math.PI * frequency / LOOPBACK_RATE);
        double previous = 0;
        double beforePrevious = 0;

        for (int i = 0; i < count; i++) {
            double current = samples[(start + i) * channels + channel] + coefficient * previous - beforePrevious;
            beforePrevious = previous;
            previous = current;
        }

        double power = previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious;
        double amplitude = 2 * Math.sqrt(Math.max(power, 0)) / count;
        return 20 * Math.log10(Math.max(amplitude, 1e-10));
    }

    // Each input hears the output of the same channel, and only it
    static SelfTestCheck evaluateLoopback(float[] samples, int inputChannels, int outputChannels, String target) {
        int frames = inputChannels == 0 ? 0 : samples.length / inputChannels;

        // One second before the end, away from the start and the end of the sine
        int end = frames - LOOPBACK_RATE * 3 / 10;
        int start = end - LOOPBACK_RATE;
        if (start < LOOPBACK_RATE / 2) {
            return SelfTestCheck.failed(AUDIO_LOOPBACK, target, "Recorded only " + frames * 1000L / LOOPBACK_RATE + " ms");
        }

        int channels = Math.min(inputChannels, outputChannels);
        List<String> problems = new ArrayList<>();
        boolean heard = false;
        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;

        for (int input = 0; input < channels; input++) {
            double[] levels = new double[outputChannels];
            int loudest = 0;
            for (int output = 0; output < outputChannels; output++) {
                levels[output] = getLevel(samples, inputChannels, input, start, LOOPBACK_RATE, getFrequency(output));
                if (levels[output] > levels[loudest]) {
                    loudest = output;
                }
            }

            int nextLoudest = -1;
            for (int output = 0; output < outputChannels; output++) {
                if (output != loudest && (nextLoudest < 0 || levels[output] > levels[nextLoudest])) {
                    nextLoudest = output;
                }
            }

            if (levels[loudest] < LOOPBACK_MIN_DBFS) {
                problems.add("In " + (input + 1) + " hears nothing");
                continue;
            }

            heard = true;

            if (loudest != input) {
                problems.add("In " + (input + 1) + " hears out " + (loudest + 1));
            } else if (nextLoudest >= 0 && levels[nextLoudest] > levels[loudest] - LOOPBACK_SEPARATION_DB) {
                problems.add("In " + (input + 1) + " hears out " + (nextLoudest + 1) + " as well, "
                        + format(levels[loudest] - levels[nextLoudest]) + " dB less");
            } else {
                lowest = Math.min(lowest, levels[loudest]);
                highest = Math.max(highest, levels[loudest]);
            }
        }

        if (!heard) {
            return SelfTestCheck.skipped(AUDIO_LOOPBACK, target, "Nothing received, no loopback cable");
        }

        if (!problems.isEmpty()) {
            return SelfTestCheck.failed(AUDIO_LOOPBACK, target, String.join(", ", problems));
        }

        String range = channels == 1 ? "1" : "1 to " + channels;
        return SelfTestCheck.passed(AUDIO_LOOPBACK, target, "In " + range + " hear out " + range + " at " + format(highest) + " to " + format(lowest) + " dBFS");
    }

    private static String format(double level) {
        return String.format(Locale.ROOT, "%.1f", level);
    }

    private synchronized void stopPipelines() {
        if (pipeline != null) {
            pipeline.stop();
            pipeline.dispose();
            pipeline = null;
        }

        if (loopbackPipeline != null) {
            loopbackPipeline.stop();
            loopbackPipeline.dispose();
            loopbackPipeline = null;
        }
    }

    @Override
    public void restore() {
        stopPipelines();
    }

}
