package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.gstreamer.TestGstreamer;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The errors of the pipelines of the self-test tell the element which failed and why, not only e.g. "Internal data
 * stream error.".
 */
class SelfTestPipelineErrorsTest {

    @Test
    void tellsTheElementAndTheReason() throws Exception {
        TestGstreamer.assumeInstalled("audiotestsrc");

        // A source without a sink stops streaming
        Pipeline pipeline = new Pipeline("selftest_errors");
        pipeline.add(ElementFactory.make("audiotestsrc", "unlinked_source"));

        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch failed = new CountDownLatch(1);
        SelfTestPipelineErrors.onError(pipeline.getBus(), message -> {
            error.compareAndSet(null, message);
            failed.countDown();
        });

        try {
            pipeline.play();
            assertTrue(failed.await(10, TimeUnit.SECONDS), "The pipeline did not fail");
        } finally {
            pipeline.stop();
            pipeline.dispose();
        }

        assertEquals("unlinked_source: Internal data stream error. (streaming stopped, reason not-linked (-1))", error.get());
    }

    @Test
    void readsTheReasonFromTheDebugInfo() {
        assertEquals("qtdemux0: Internal data stream error. (streaming stopped, reason not-negotiated (-4))",
                SelfTestPipelineErrors.describe("qtdemux0", "Internal data stream error.", """
                        ../libs/gst/base/gstbasesrc.c(3132): gst_base_src_loop (): /GstPipeline:selftest_videodecoder\
                        /GstURIDecodeBin:selftest_uridecodebin/GstDecodeBin:decodebin0/GstQTDemux:qtdemux0:
                        streaming stopped, reason not-negotiated (-4)"""));

        // Debug info only telling where it happened, or none
        assertEquals("v4l2slh265dec0: Could not decode stream.", SelfTestPipelineErrors.describe("v4l2slh265dec0",
                "Could not decode stream.", "../sys/v4l2codecs/gstv4l2codech265dec.c(1133): gst_v4l2_codec_h265_dec_end_picture (): /GstPipeline:p"));
        assertEquals("Could not decode stream.", SelfTestPipelineErrors.describe(null, "Could not decode stream.", null));
        assertNull(SelfTestPipelineErrors.getReason("gstfoo.c(1): foo (): /GstPipeline:p:\n "));
    }
}
