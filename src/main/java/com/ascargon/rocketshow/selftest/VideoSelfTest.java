package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.play.CompositionPipelineBuilder;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.video.HdmiService;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plays a test picture on the screen, with the sink of the videos of a composition. Whether a picture appears needs
 * someone looking or a capture device.
 */
@Service
class VideoSelfTest implements SelfTestStep {

    static final String VIDEO = "video";

    private static final long START_MILLIS = 5000;

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
        return List.of(VIDEO);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        if (!capabilitiesService.getCapabilities().isGstreamer()) {
            context.report(SelfTestCheck.skipped(VIDEO, null, "GStreamer is not available"));
            return;
        }

        if (!hdmiService.isDisplayConnected()) {
            context.report(SelfTestCheck.skipped(VIDEO, null, "No display connected"));
            return;
        }

        CountDownLatch playing = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        String sinkName;

        try {
            Element sink = compositionPipelineBuilder.createVideoSink();
            sinkName = sink.getFactory().getName();
            Pipeline pipeline = buildPipeline(sink);

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

            pipeline.play();

            if (!playing.await(START_MILLIS, TimeUnit.MILLISECONDS)) {
                context.report(SelfTestCheck.failed(VIDEO, null, "Did not start playing within " + START_MILLIS / 1000 + " s"));
                return;
            }

            if (error.get() == null && !context.await(pictureMillis)) {
                context.report(SelfTestCheck.skipped(VIDEO, null, SelfTestContext.STOPPED));
                return;
            }
        } finally {
            stopPipeline();
        }

        if (error.get() != null) {
            context.report(SelfTestCheck.failed(VIDEO, null, error.get()));
        } else {
            context.report(SelfTestCheck.passed(VIDEO, null, "Test picture played for " + pictureMillis / 1000.0 + " s on " + sinkName));
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
