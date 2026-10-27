package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.video.HdmiService;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.AppSink;
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
 * like a composition does (with the hardware decoder, if the unit has one). Whether a picture appears needs someone
 * looking or a capture device.
 */
@Service
class VideoSelfTest implements SelfTestStep {

    static final String VIDEO = "video";
    static final String VIDEO_DECODER = "video-decoder";

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
        return List.of(VIDEO, VIDEO_DECODER);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            context.report(SelfTestCheck.skipped(VIDEO, null, "GStreamer is not available"));
            context.report(SelfTestCheck.skipped(VIDEO_DECODER, null, "GStreamer is not available"));
            return;
        }

        playPicture(context);

        if (context.isStopped()) {
            context.report(SelfTestCheck.skipped(VIDEO_DECODER, null, SelfTestContext.STOPPED));
        } else {
            context.report(decode());
        }
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
            SelfTestPipelineErrors.onError(bus, message -> {
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

    // Like the video files of a composition: by uridecodebin, which picks the decoder with the highest rank
    private SelfTestCheck decode() throws Exception {
        for (String element : List.of("uridecodebin", "appsink")) {
            if (!isInstalled(element)) {
                return SelfTestCheck.skipped(VIDEO_DECODER, null, "The GStreamer element " + element + " is not installed");
            }
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

                AppSink sink = (AppSink) ElementFactory.make("appsink", "selftest_decodedsink");
                sink.set("emit-signals", true);
                sink.set("sync", false);
                sink.connect((AppSink.NEW_SAMPLE) element -> {
                    element.pullSample().dispose();
                    frames.incrementAndGet();
                    return FlowReturn.OK;
                });
                pipeline.add(sink);

                uriDecodeBin.connect((Element.PAD_ADDED) (Element element, Pad pad) -> {
                    Caps caps = pad.getCurrentCaps();
                    if (caps != null && caps.getStructure(0).getName().startsWith("video/x-raw")) {
                        pad.link(sink.getStaticPad("sink"));
                    }
                });

                Bus bus = pipeline.getBus();
                SelfTestPipelineErrors.onError(bus, message -> {
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
