package com.ascargon.rocketshow.gstreamer;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The errors of the pipelines tell the element which failed and why, not only e.g. "Internal data stream error.", and
 * where the elements could not agree on a format, between which ones and on which formats.
 */
class GstPipelineErrorsTest {

    @TempDir
    File directory;

    private static String failToPlay(Pipeline pipeline) throws Exception {
        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch failed = new CountDownLatch(1);
        GstPipelineErrors.onError(pipeline.getBus(), message -> {
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

        return error.get();
    }

    @Test
    void tellsTheElementAndTheReason() throws Exception {
        TestGstreamer.assumeInstalled("audiotestsrc");

        // A source without a sink stops streaming
        Pipeline pipeline = new Pipeline("errors");
        pipeline.add(ElementFactory.make("audiotestsrc", "unlinked_source"));

        assertEquals("unlinked_source: Internal data stream error. (streaming stopped, reason not-linked (-1))", failToPlay(pipeline));
    }

    @Test
    void tellsTheFormatsWhichHaveNothingInCommon() throws Exception {
        TestGstreamer.assumeInstalled("audiotestsrc", "capsfilter", "fakesink");

        // Linked before the formats are set, which then have nothing in common
        Pipeline pipeline = new Pipeline("errors");
        Element source = ElementFactory.make("audiotestsrc", "source");
        Element first = ElementFactory.make("capsfilter", "rate44100");
        Element second = ElementFactory.make("capsfilter", "rate48000");
        Element sink = ElementFactory.make("fakesink", "sink");
        pipeline.addMany(source, first, second, sink);
        Element.linkMany(source, first, second, sink);
        first.set("caps", Caps.fromString("audio/x-raw,rate=44100"));
        second.set("caps", Caps.fromString("audio/x-raw,rate=48000"));

        String[] error = failToPlay(pipeline).split("\n");

        assertEquals(2, error.length);
        assertEquals("source: Internal data stream error. (streaming stopped, reason not-negotiated (-4))", error[0]);
        assertTrue(error[1].startsWith("Could not negotiate rate44100:src -> rate48000:sink (mismatch in rate). Offered: audio/x-raw, rate=44100, "), error[1]);
        assertTrue(error[1].endsWith(". Accepted: audio/x-raw, rate=48000"), error[1]);
    }

    private File encodeSixChannels() throws Exception {
        File file = new File(directory, "six_channels.wav");
        TestGstreamer.encode("audiotestsrc num-buffers=10 ! audio/x-raw,channels=6 ! wavenc ! filesink location=" + file.getAbsolutePath());
        return file;
    }

    @Test
    void tellsTheFormatWhichWasNotAccepted() throws Exception {
        TestGstreamer.assumeInstalled("audiotestsrc", "wavenc", "wavparse", "capsfilter", "fakesink");

        // The decoded file has 6 channels, the elements after it only take 2 when it plays
        Pipeline pipeline = new Pipeline("errors");
        URIDecodeBin decodeBin = (URIDecodeBin) ElementFactory.make("uridecodebin", "decodebin");
        decodeBin.set("uri", encodeSixChannels().toURI().toString());
        Element stereo = ElementFactory.make("capsfilter", "stereo");
        Element sink = ElementFactory.make("fakesink", "sink");
        pipeline.addMany(decodeBin, stereo, sink);
        stereo.link(sink);
        decodeBin.connect((Element.PAD_ADDED) (element, pad) -> {
            pad.link(stereo.getSinkPads().get(0));
            stereo.set("caps", Caps.fromString("audio/x-raw,channels=2"));
        });

        String[] error = failToPlay(pipeline).split("\n");

        // Through the ghost pads of the decodebin, from the element inside
        assertEquals(2, error.length);
        assertTrue(error[0].matches("wavparse\\d+: Internal data stream error\\. \\(streaming stopped, reason not-negotiated \\(-4\\)\\)"), error[0]);
        assertTrue(error[1].matches("Could not negotiate wavparse\\d+:src -> stereo:sink \\(mismatch in channels\\)\\. Offered: audio/x-raw, .*channels=6.*\\. Accepted: audio/x-raw, channels=2"), error[1]);
    }

    @Test
    void tellsWhyAStreamCouldNotBeLinked() throws Exception {
        TestGstreamer.assumeInstalled("audiotestsrc", "wavenc", "wavparse", "capsfilter", "fakesink");

        // The decoded file has 6 channels, the elements after it only take 2
        Pipeline pipeline = new Pipeline("errors");
        URIDecodeBin decodeBin = (URIDecodeBin) ElementFactory.make("uridecodebin", "decodebin");
        decodeBin.set("uri", encodeSixChannels().toURI().toString());
        Element stereo = ElementFactory.make("capsfilter", "stereo");
        stereo.set("caps", Caps.fromString("audio/x-raw,channels=2"));
        Element sink = ElementFactory.make("fakesink", "sink");
        pipeline.addMany(decodeBin, stereo, sink);
        stereo.link(sink);
        AtomicReference<String> failure = new AtomicReference<>();
        decodeBin.connect((Element.PAD_ADDED) (element, pad) -> failure.set(GstPipelineErrors.link(pad, stereo.getSinkPads().get(0))));

        // GStreamer only tells that nothing took the stream
        assertTrue(failToPlay(pipeline).endsWith("(streaming stopped, reason not-linked (-1))"));
        assertTrue(failure.get().startsWith("Could not link decodebin:src_0 -> stereo:sink (mismatch in channels). Offered: audio/x-raw, "), failure.get());
        assertTrue(failure.get().contains("channels=6"), failure.get());
        assertTrue(failure.get().endsWith(". Accepted: audio/x-raw, channels=2"), failure.get());
    }

    @Test
    void linksThePadsWhichAgree() {
        TestGstreamer.assumeInstalled("audiotestsrc", "fakesink");

        Element source = ElementFactory.make("audiotestsrc", "source");
        Element sink = ElementFactory.make("fakesink", "sink");

        assertNull(GstPipelineErrors.link(source.getSrcPads().get(0), sink.getSinkPads().get(0)));
        assertTrue(source.getSrcPads().get(0).isLinked());
    }

    @Test
    void readsTheReasonFromTheDebugInfo() {
        assertEquals("qtdemux0: Internal data stream error. (streaming stopped, reason not-negotiated (-4))",
                GstPipelineErrors.describe("qtdemux0", "Internal data stream error.", """
                        ../libs/gst/base/gstbasesrc.c(3132): gst_base_src_loop (): /GstPipeline:selftest_videodecoder\
                        /GstURIDecodeBin:selftest_uridecodebin/GstDecodeBin:decodebin0/GstQTDemux:qtdemux0:
                        streaming stopped, reason not-negotiated (-4)"""));

        // Debug info only telling where it happened, or none
        assertEquals("v4l2slh265dec0: Could not decode stream.", GstPipelineErrors.describe("v4l2slh265dec0",
                "Could not decode stream.", "../sys/v4l2codecs/gstv4l2codech265dec.c(1133): gst_v4l2_codec_h265_dec_end_picture (): /GstPipeline:p"));
        assertEquals("Could not decode stream.", GstPipelineErrors.describe(null, "Could not decode stream.", null));
        assertNull(GstPipelineErrors.getReason("gstfoo.c(1): foo (): /GstPipeline:p:\n "));
    }

    @Test
    void readsTheElementDirectlyInThePipelineFromTheDebugInfo() {
        assertEquals("audiouridecodebin0", GstPipelineErrors.getTopLevelName(null, """
                ../plugins/elements/gsttypefindelement.c(1175): gst_type_find_element_loop (): /GstPipeline:pipeline0\
                /GstURIDecodeBin:audiouridecodebin0/GstDecodeBin:decodebin0/GstTypeFindElement:typefind:
                streaming stopped, reason error (-5)"""));
        assertEquals("source", GstPipelineErrors.getTopLevelName(null, """
                ../libs/gst/base/gstbasesrc.c(3177): gst_base_src_loop (): /GstPipeline:errors/GstAudioTestSrc:source:
                streaming stopped, reason not-negotiated (-4)"""));
        assertEquals("audiosink_hw0", GstPipelineErrors.getTopLevelName(null,
                "../ext/alsa/gstalsasink.c(894): gst_alsasink_open (): /GstPipeline:pipeline0/GstAlsaSink:audiosink_hw0"));

        // The pipeline itself, or no path
        assertNull(GstPipelineErrors.getTopLevelName(null, "gstbin.c(1): foo (): /GstPipeline:pipeline0"));
        assertNull(GstPipelineErrors.getTopLevelName(null, null));
    }

    @Test
    void tellsTheFieldsWhichDoNotMatch() {
        TestGstreamer.assumeInstalled();

        Caps sixChannels = Caps.fromString("audio/x-raw, rate=44100, channels=6, channel-mask=(bitmask)0x3f");
        assertEquals(List.of("channels"), GstPipelineErrors.getMismatchingFields(sixChannels, Caps.fromString("audio/x-raw, channels=2")));
        assertEquals(List.of("rate", "channels"), GstPipelineErrors.getMismatchingFields(sixChannels,
                Caps.fromString("audio/x-raw, rate=48000, channels=6; audio/x-raw, rate=44100, channels=2")));

        // Only both together, or a whole other format
        assertEquals(List.of(), GstPipelineErrors.getMismatchingFields(sixChannels, Caps.fromString("audio/x-raw, rate=48000, channels=2")));
        assertEquals(List.of(), GstPipelineErrors.getMismatchingFields(sixChannels, Caps.fromString("video/x-raw")));
    }

    @Test
    void writesTheFormatsWithoutTheTypes() {
        TestGstreamer.assumeInstalled();

        assertEquals("audio/x-raw, rate=44100, channels=2, format=S16LE",
                GstPipelineErrors.toString(Caps.fromString("audio/x-raw, rate=(int)44100, channels=(int)2, format=(string)S16LE")));
        assertEquals("video/x-raw(memory:DMABuf), framerate=30/1",
                GstPipelineErrors.toString(Caps.fromString("video/x-raw(memory:DMABuf), framerate=(fraction)30/1")));
        assertEquals("nothing", GstPipelineErrors.toString(Caps.emptyCaps()));
        assertEquals("anything", GstPipelineErrors.toString(Caps.anyCaps()));
    }
}
