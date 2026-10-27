package com.ascargon.rocketshow.gstreamer;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GStreamer for the tests playing real media. They are skipped where GStreamer or one of the elements they need is
 * not installed (e.g. locally). CI installs it and fails instead of skipping them (GSTREAMER_TESTS_REQUIRED=true).
 */
public class TestGstreamer {

    private TestGstreamer() {
    }

    private static void assumeOrRequire(boolean installed, String message) {
        if ("true".equals(System.getenv("GSTREAMER_TESTS_REQUIRED"))) {
            assertTrue(installed, message);
        } else {
            assumeTrue(installed, message);
        }
    }

    public static void assumeInstalled(String... elements) {
        boolean initialized;

        try {
            if (!Gst.isInitialized()) {
                Gst.init();
            }
            initialized = true;
        } catch (Throwable e) {
            initialized = false;
        }

        assumeOrRequire(initialized, "GStreamer is not installed");

        for (String element : elements) {
            boolean installed;

            try {
                installed = ElementFactory.find(element) != null;
            } catch (IllegalArgumentException e) {
                installed = false;
            }

            assumeOrRequire(installed, "The GStreamer element " + element + " is not installed");
        }
    }

    // Runs a pipeline in the syntax of gst-launch until its end, e.g. to encode the media of a test
    public static void encode(String pipelineDescription) throws Exception {
        Pipeline pipeline = (Pipeline) Gst.parseLaunch(pipelineDescription);
        CountDownLatch written = new CountDownLatch(1);
        pipeline.getBus().connect((Bus.EOS) source -> written.countDown());
        pipeline.getBus().connect((Bus.ERROR) (source, code, message) -> written.countDown());
        pipeline.play();
        assertTrue(written.await(30, TimeUnit.SECONDS), "Could not encode " + pipelineDescription);
        pipeline.stop();
        pipeline.dispose();
    }

}
