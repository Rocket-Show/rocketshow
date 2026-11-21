package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.gstreamer.GstPipelineErrors;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.video.HdmiService;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadDirection;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.PluginFeature;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Plays a test picture on the screen, with the sink of the videos of a composition, and decodes a short H.265 video
 * onto it like a composition does (with the hardware decoder, if the unit has one). Whether a picture appears needs
 * someone looking or a capture device. Also tells whether the unit has the decoder of FFmpeg for H.264 videos.
 */
@Service
class VideoSelfTest implements SelfTestStep {

    static final String VIDEO = "video";
    static final String VIDEO_DECODER = "video-decoder";
    static final String H264_DECODER = "h264-decoder";

    // The decoder of FFmpeg for H.264 (package gstreamer1.0-libav). Without it, a unit without a hardware decoder for
    // H.264 (e.g. a Raspberry Pi 5) decodes H.264 with openh264dec, if at all: on one CPU core and not every H.264
    // video. On a unit at an LED processor, it stopped videos of compositions with "not-negotiated".
    static final String AVDEC_H264 = "avdec_h264";
    private static final String OPENH264DEC = "openh264dec";

    // A video of 10 frames of 640x360 in H.265
    static final String H265_VIDEO = "/selftest/h265.mp4";
    static final int H265_FRAMES = 10;

    private static final long START_MILLIS = 5000;
    private static final long DECODE_MILLIS = 10000;

    private final HdmiService hdmiService;
    private final CompositionPipelineBuilder compositionPipelineBuilder;
    private final CapabilitiesService capabilitiesService;

    // How long the picture plays. Package visible for the tests.
    long pictureMillis = 3000;

    private Pipeline pipeline;

    VideoSelfTest(
            HdmiService hdmiService,
            CompositionPipelineBuilder compositionPipelineBuilder,
            CapabilitiesService capabilitiesService,

            // import to make sure, Gstreamer is initialized before using it here
            GstreamerInitService gstreamerInitService
    ) {
        this.hdmiService = hdmiService;
        this.compositionPipelineBuilder = compositionPipelineBuilder;
        this.capabilitiesService = capabilitiesService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(VIDEO, VIDEO_DECODER, H264_DECODER);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            context.report(SelfTestCheck.skipped(VIDEO, null, "GStreamer is not available"));
            context.report(SelfTestCheck.skipped(VIDEO_DECODER, null, "GStreamer is not available"));
            context.report(SelfTestCheck.skipped(H264_DECODER, null, "GStreamer is not available"));
            return;
        }

        playPicture(context);

        if (context.isStopped()) {
            context.report(SelfTestCheck.skipped(VIDEO_DECODER, null, SelfTestContext.STOPPED));
        } else {
            context.report(decode());
        }

        // Needs no display: only which decoders GStreamer has
        context.report(checkH264Decoder(getH264Decoders()));
    }

    // A decoder GStreamer has, e.g. "avdec_h264 (software)"
    record Decoder(String name, boolean hardware) {

        @Override
        public String toString() {
            return name + (hardware ? " (hardware)" : " (software)");
        }
    }

    // The decoders GStreamer has for H.264, the one uridecodebin picks for the videos of compositions first (by rank)
    static List<Decoder> getH264Decoders() {
        return ElementFactory.listGetElementsFilter(ElementFactory.ListType.DECODER, PluginFeature.Rank.MARGINAL,
                        Caps.fromString("video/x-h264"), PadDirection.SINK, false).stream()
                .map(factory -> new Decoder(factory.getName(), factory.getKlass().contains("Hardware")))
                .toList();
    }

    // Whether the decoder of FFmpeg for H.264 is installed. The decoder uridecodebin picks goes into the target, e.g. a
    // hardware decoder on a Raspberry Pi 4. Package visible for the tests.
    static SelfTestCheck checkH264Decoder(List<Decoder> decoders) {
        Decoder picked = decoders.isEmpty() ? null : decoders.getFirst();
        String target = picked == null ? null : picked.toString();

        if (decoders.stream().anyMatch(decoder -> decoder.name().equals(AVDEC_H264))) {
            return SelfTestCheck.passed(H264_DECODER, target, AVDEC_H264 + " is installed");
        }

        String missing = AVDEC_H264 + " is not installed (package gstreamer1.0-libav)";

        if (picked == null) {
            return SelfTestCheck.failed(H264_DECODER, null, missing + ", nor any other decoder: H.264 videos don't play");
        }

        if (picked.name().equals(OPENH264DEC)) {
            return SelfTestCheck.failed(H264_DECODER, target, missing + ": H.264 videos decode with " + OPENH264DEC
                    + " instead, on one CPU core, and not every H.264 video");
        }

        return SelfTestCheck.failed(H264_DECODER, target, missing + ": H.264 videos only decode with " + picked.name());
    }

    private static String describe(HdmiService.ConnectedDisplay connectedDisplay) {
        StringBuilder description = new StringBuilder(connectedDisplay.connector());
        if (connectedDisplay.name() != null) {
            description.append(" ").append(connectedDisplay.name());
        }
        if (connectedDisplay.mode() != null) {
            description.append(" ").append(connectedDisplay.mode());
        }
        return description.toString();
    }

    private void playPicture(SelfTestContext context) throws Exception {
        List<HdmiService.ConnectedDisplay> connectedDisplays = hdmiService.getConnectedDisplays();

        if (connectedDisplays.isEmpty()) {
            context.report(SelfTestCheck.skipped(VIDEO, null, "No display connected"));
            return;
        }

        // E.g. "HDMI-A-1 DELL U2415 1920x1200"
        String target = connectedDisplays.stream().map(VideoSelfTest::describe).collect(Collectors.joining(", "));

        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        String sinkName;

        try {
            Element sink = compositionPipelineBuilder.createVideoSink();
            sinkName = sink.getFactory().getName();
            Pipeline pipeline = buildPipeline(sink);

            Bus bus = pipeline.getBus();
            GstPipelineErrors.onError(bus, message -> {
                error.compareAndSet(null, message);
                playing.countDown();
            });
            bus.connect((Bus.STATE_CHANGED) (source, old, current, pending) -> {
                if (source.getTypeName().equals("GstPipeline") && current == State.PLAYING) {
                    playing.countDown();
                }
            });

            pipeline.play();

            if (!playing.await(START_MILLIS, TimeUnit.MILLISECONDS)) {
                context.report(SelfTestCheck.failed(VIDEO, target, "Did not start playing within " + START_MILLIS / 1000 + " s"));
                return;
            }

            if (error.get() == null && !context.await(pictureMillis)) {
                context.report(SelfTestCheck.skipped(VIDEO, target, SelfTestContext.STOPPED));
                return;
            }
        } finally {
            stopPipeline();
        }

        if (error.get() != null) {
            context.report(SelfTestCheck.failed(VIDEO, target, error.get()));
        } else {
            context.report(SelfTestCheck.passed(VIDEO, target, "Test picture played for " + pictureMillis / 1000.0 + " s on " + sinkName));
        }
    }

    private synchronized Pipeline buildPipeline(Element sink) {
        pipeline = new Pipeline("selftest_video");

        Element videoTestSrc = ElementFactory.make("videotestsrc", "selftest_videotestsrc");
        videoTestSrc.set("is-live", true);
        pipeline.add(videoTestSrc);

        Element videoConvert = ElementFactory.make("videoconvert", "selftest_videoconvert");
        pipeline.add(videoConvert);

        pipeline.add(sink);

        videoTestSrc.link(videoConvert);
        videoConvert.link(sink);

        return pipeline;
    }

    private static boolean isInstalled(String element) {
        try {
            return ElementFactory.find(element) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Like the video files of a composition: uridecodebin picks the decoder with the highest rank, and the frames go
    // through videoconvert to the sink of the videos. Only that sink takes the frames of the hardware decoder of a
    // Raspberry Pi: v4l2slh265dec hands over DMABuf, which needs the video meta and the buffer pool of kmssink. Other
    // sinks fail ("not-negotiated", "Failed to configure the buffer pool"), also when asking for system memory.
    private SelfTestCheck decode() throws Exception {
        if (!isInstalled("uridecodebin")) {
            return SelfTestCheck.skipped(VIDEO_DECODER, null, "The GStreamer element uridecodebin is not installed");
        }

        if (hdmiService.getConnectedDisplays().isEmpty()) {
            return SelfTestCheck.skipped(VIDEO_DECODER, null, "No display connected, the video decodes onto it like the videos of compositions");
        }

        Path video = Files.createTempFile("rocketshow-selftest", ".mp4");

        try (InputStream inputStream = VideoSelfTest.class.getResourceAsStream(H265_VIDEO)) {
            Files.copy(inputStream, video, StandardCopyOption.REPLACE_EXISTING);
        }

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        AtomicInteger frames = new AtomicInteger();
        String decoder;

        try {
            URIDecodeBin uriDecodeBin;

            synchronized (this) {
                pipeline = new Pipeline("selftest_videodecoder");

                uriDecodeBin = (URIDecodeBin) ElementFactory.make("uridecodebin", "selftest_uridecodebin");
                uriDecodeBin.set("uri", video.toUri().toString());
                pipeline.add(uriDecodeBin);

                Element queue = ElementFactory.make("queue", "selftest_decodedqueue");
                pipeline.add(queue);

                Element videoConvert = ElementFactory.make("videoconvert", "selftest_decodedconvert");
                pipeline.add(videoConvert);

                Element sink = compositionPipelineBuilder.createVideoSink();
                sink.set("sync", false);
                sink.getStaticPad("sink").addDataProbe((pad, buffer) -> {
                    frames.incrementAndGet();
                    return PadProbeReturn.OK;
                });
                pipeline.add(sink);

                queue.link(videoConvert);
                videoConvert.link(sink);

                // GStreamer only tells afterwards that nothing took the stream, if it could not be linked
                uriDecodeBin.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
                    Caps caps = pad.getCurrentCaps();
                    if (caps != null && caps.getStructure(0).getName().startsWith("video/x-raw")) {
                        String failure = GstPipelineErrors.link(pad, queue.getStaticPad("sink"));
                        if (failure != null) {
                            error.compareAndSet(null, failure);
                        }
                    }
                });

                Bus bus = pipeline.getBus();
                GstPipelineErrors.onError(bus, message -> {
                    error.compareAndSet(null, message);
                    done.countDown();
                });
                bus.connect((Bus.EOS) source -> done.countDown());
            }

            pipeline.play();

            if (!done.await(DECODE_MILLIS, TimeUnit.MILLISECONDS)) {
                return SelfTestCheck.failed(VIDEO_DECODER, null, "Decoded " + frames.get() + " of " + H265_FRAMES + " frames within " + DECODE_MILLIS / 1000 + " s");
            }

            decoder = getDecoder(uriDecodeBin.getElementsRecursive());
        } finally {
            stopPipeline();
            Files.deleteIfExists(video);
        }

        if (error.get() != null) {
            if (error.get().contains("missing a plug-in") || error.get().contains("No decoder")) {
                return SelfTestCheck.skipped(VIDEO_DECODER, null, "No H.265 decoder: " + error.get());
            }
            return SelfTestCheck.failed(VIDEO_DECODER, null, error.get());
        }

        if (frames.get() != H265_FRAMES) {
            return SelfTestCheck.failed(VIDEO_DECODER, null, "Decoded " + frames.get() + " of " + H265_FRAMES + " frames of H.265 with " + decoder);
        }

        return SelfTestCheck.passed(VIDEO_DECODER, decoder, H265_FRAMES + " frames of H.265 decoded");
    }

    // The video decoder uridecodebin picked, e.g. "v4l2slh265dec (hardware)"
    private static String getDecoder(List<Element> elements) {
        for (Element element : elements) {
            ElementFactory factory = element.getFactory();
            String klass = factory == null ? "" : factory.getKlass();
            if (klass.contains("Decoder") && klass.contains("Video")) {
                return factory.getName() + (klass.contains("Hardware") ? " (hardware)" : " (software)");
            }
        }
        return "an unknown decoder";
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
