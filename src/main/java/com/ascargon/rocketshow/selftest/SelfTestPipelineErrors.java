package com.ascargon.rocketshow.selftest;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.lowlevel.GlibAPI;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GErrorStruct;
import org.freedesktop.gstreamer.lowlevel.GstMessageAPI;
import org.freedesktop.gstreamer.message.Message;
import org.freedesktop.gstreamer.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * The errors of the pipelines of the self-test. The message of GStreamer alone often tells little, e.g. "Internal data
 * stream error.", so the element which failed and the reason of the debug info go with it, e.g. "qtdemux0: Internal
 * data stream error. (streaming stopped, reason not-negotiated (-4))". The whole debug info goes into the log.
 */
final class SelfTestPipelineErrors {

    private final static Logger logger = LoggerFactory.getLogger(SelfTestPipelineErrors.class);

    private SelfTestPipelineErrors() {
    }

    static void onError(Bus bus, Consumer<String> listener) {
        bus.connect((Bus.MESSAGE) (source, message) -> {
            if (message.getType() == MessageType.ERROR) {
                listener.accept(describe(message));
            }
        });
    }

    private static String describe(Message message) {
        PointerByReference error = new PointerByReference();
        PointerByReference debug = new PointerByReference();
        GstMessageAPI.GSTMESSAGE_API.gst_message_parse_error(message, error, debug);

        String text;
        String debugInfo = null;
        try {
            text = new GErrorStruct(error.getValue()).getMessage();
            if (debug.getValue() != null) {
                debugInfo = debug.getValue().getString(0);
            }
        } finally {
            GlibAPI.GLIB_API.g_error_free(error.getValue());
            free(debug.getValue());
        }

        String element = message.getSource() == null ? null : message.getSource().getName();
        logger.warn("GStreamer error in the self-test from {}: {}\n{}", element, text, debugInfo);

        return describe(element, text, debugInfo);
    }

    private static void free(Pointer pointer) {
        if (pointer != null) {
            GlibAPI.GLIB_API.g_free(pointer);
        }
    }

    // E.g. "qtdemux0: Internal data stream error. (streaming stopped, reason not-negotiated (-4))"
    static String describe(String element, String text, String debugInfo) {
        String reason = getReason(debugInfo);
        return (element == null ? "" : element + ": ") + text + (reason == null ? "" : " (" + reason + ")");
    }

    // The last line of the debug info, which follows where it happened, e.g. "streaming stopped, reason
    // not-negotiated (-4)" of "../libs/gst/base/gstbasesrc.c(3132): gst_base_src_loop ():
    // /GstPipeline:selftest_videodecoder/.../GstQTDemux:qtdemux0:\nstreaming stopped, reason not-negotiated (-4)"
    static String getReason(String debugInfo) {
        if (debugInfo == null || !debugInfo.contains("\n")) {
            return null;
        }

        String reason = debugInfo.substring(debugInfo.lastIndexOf('\n') + 1).strip();
        return reason.isEmpty() ? null : reason;
    }
}
