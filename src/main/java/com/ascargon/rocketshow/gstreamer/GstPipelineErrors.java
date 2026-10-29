package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.PadLinkReturn;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.lowlevel.GlibAPI;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GErrorStruct;
import org.freedesktop.gstreamer.lowlevel.GstMessageAPI;
import org.freedesktop.gstreamer.message.Message;
import org.freedesktop.gstreamer.message.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The errors of the GStreamer pipelines. The message of GStreamer alone often tells little, e.g. "Internal data stream
 * error.", so the element which failed and the reason of the debug info go with it, e.g. "qtdemux0: Internal data
 * stream error. (streaming stopped, reason not-negotiated (-4))". If the elements could not agree on a format, a line
 * for each link where they failed follows, with what did not match and the format offered and the one accepted, e.g.
 * "Could not negotiate wavparse0:src -> audioconvert0:sink (mismatch in channels). Offered: audio/x-raw, ...,
 * channels=6. Accepted: audio/x-raw, ..., channels=2". The whole debug info and the formats go into the log.
 */
public final class GstPipelineErrors {

    private final static Logger logger = LoggerFactory.getLogger(GstPipelineErrors.class);

    // The formats are cut after this many characters for the web app, e.g. the long lists of the video sinks
    private final static int MAX_CAPS_LENGTH = 300;

    // The name of the element directly in the pipeline in the path of the debug info, e.g. "audiouridecodebin0" of
    // "...: gst_type_find_element_loop (): /GstPipeline:pipeline0/GstURIDecodeBin:audiouridecodebin0/GstDecodeBin:..."
    private final static Pattern TOP_LEVEL_PATH = Pattern.compile("\\(\\): /[^/\\n]+/[^:/\\n]+:([^/\\n]+?):?(?:/|\\n|$)");

    private GstPipelineErrors() {
    }

    public interface Listener {

        // The name of the element directly in the pipeline, which the one which posted the error is part of (e.g. the
        // uridecodebin of a demuxer, to tell which file it played, or null), and the description of the error
        void onError(String topLevelElement, String description);
    }

    public static void onError(Bus bus, Consumer<String> listener) {
        onError(bus, (topLevelElement, description) -> listener.accept(description));
    }

    public static void onError(Bus bus, Listener listener) {
        bus.connect((Bus.MESSAGE) (source, message) -> {
            if (message.getType() == MessageType.ERROR) {
                ErrorInfo error = ErrorInfo.of(message);
                listener.onError(getTopLevelName(message.getSource(), error.debugInfo()), describe(message.getSource(), error));
            }
        });
    }

    // Link the pads, e.g. a stream of a decodebin as soon as it appeared. Returns why they could not be linked, or null
    // if they could. GStreamer only tells afterwards that the stream was not linked ("reason not-linked (-1)").
    public static String link(Pad src, Pad sink) {
        try {
            src.link(sink);
            return null;
        } catch (PadLinkException e) {
            if (e.getLinkResult() != PadLinkReturn.NOFORMAT) {
                String failure = "Could not link " + getName(src) + " -> " + getName(sink) + " (" + e.getLinkResult() + ")";
                logger.error(failure);
                return failure;
            }

            Caps offered = src.hasCurrentCaps() ? src.getCurrentCaps() : src.queryCaps(null);
            FailedLink failedLink = new FailedLink("Could not link", src, sink, offered, sink.queryCaps(null));
            logger.error(failedLink.describe(Integer.MAX_VALUE));
            return failedLink.describe(MAX_CAPS_LENGTH);
        }
    }

    // The name of the element directly in the pipeline the given one is part of, e.g. the uridecodebin of a demuxer.
    // From the path in the debug info, where it was when it failed: it may be out of the pipeline already, e.g. when
    // the pipeline stopped on an error before.
    static String getTopLevelName(GstObject source, String debugInfo) {
        Matcher matcher = TOP_LEVEL_PATH.matcher(debugInfo == null ? "" : debugInfo);
        if (matcher.find()) {
            return matcher.group(1);
        }

        GstObject object = source;
        while (object != null) {
            GstObject parent = object.getParent();
            if (parent == null || parent.getParent() == null) {
                return parent == null ? null : object.getName();
            }
            object = parent;
        }
        return null;
    }

    private static GstObject getPipeline(GstObject object) {
        while (object != null && object.getParent() != null) {
            object = object.getParent();
        }
        return object;
    }

    // The text and the debug info of an error message
    private record ErrorInfo(String text, String debugInfo) {

        static ErrorInfo of(Message message) {
            PointerByReference error = new PointerByReference();
            PointerByReference debug = new PointerByReference();
            GstMessageAPI.GSTMESSAGE_API.gst_message_parse_error(message, error, debug);

            try {
                return new ErrorInfo(new GErrorStruct(error.getValue()).getMessage(), debug.getValue() == null ? null : debug.getValue().getString(0));
            } finally {
                GlibAPI.GLIB_API.g_error_free(error.getValue());
                free(debug.getValue());
            }
        }

        private static void free(Pointer pointer) {
            if (pointer != null) {
                GlibAPI.GLIB_API.g_free(pointer);
            }
        }
    }

    private static String describe(GstObject source, ErrorInfo error) {
        String element = source == null ? null : source.getName();
        StringBuilder log = new StringBuilder();
        StringBuilder description = new StringBuilder(describe(element, error.text(), error.debugInfo()));

        // The pipeline still is as it was when it failed: the listeners only stop it afterwards
        if ((isNegotiation(error.text()) || isNegotiation(error.debugInfo())) && getPipeline(source) instanceof Bin pipeline) {
            try {
                for (FailedLink failedLink : findFailedLinks(pipeline)) {
                    log.append('\n').append(failedLink.describe(Integer.MAX_VALUE));
                    description.append('\n').append(failedLink.describe(MAX_CAPS_LENGTH));
                }
            } catch (RuntimeException e) {
                // The error still has to reach the listener
                logger.warn("Could not find the elements which did not agree on a format", e);
            }
        }
        logger.warn("GStreamer error from {}: {}\n{}{}", element, error.text(), error.debugInfo(), log);

        return description.toString();
    }

    // E.g. "Internal data stream error." with the debug info "... reason not-negotiated (-4)"
    private static boolean isNegotiation(String text) {
        return text != null && text.toLowerCase().contains("negotiat");
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

    // E.g. "audioconvert0:sink"
    private static String getName(Pad pad) {
        Element element = pad.getParentElement();
        return (element == null ? "" : element.getName() + ":") + pad.getName();
    }

    // A link between the pads of two elements, which did not agree on a format
    record FailedLink(String problem, Pad src, Pad sink, Caps offered, Caps accepted) {

        // E.g. "Could not negotiate wavparse0:src -> audioconvert0:sink (mismatch in channels). Offered: audio/x-raw,
        // ..., channels=6. Accepted: audio/x-raw, ..., channels=2"
        String describe(int maxCapsLength) {
            List<String> fields = getMismatchingFields(offered, accepted);
            return problem + " " + getName(src) + " -> " + getName(sink)
                    + (fields.isEmpty() ? "" : " (mismatch in " + String.join(" or ", fields) + ")")
                    + ". Offered: " + shorten(GstPipelineErrors.toString(offered), maxCapsLength)
                    + ". Accepted: " + shorten(GstPipelineErrors.toString(accepted), maxCapsLength);
        }

        private static String shorten(String caps, int maxLength) {
            return caps.length() <= maxLength ? caps : caps.substring(0, maxLength) + "...";
        }
    }

    // The links where the elements could not agree on a format: the ones where the upstream element offered a format
    // the downstream one did not accept, otherwise the ones where the formats both can handle have nothing in common.
    // Only the last one of a chain of them, because the elements before it mostly only failed because it did.
    static List<FailedLink> findFailedLinks(Bin bin) {
        List<FailedLink> refused = new ArrayList<>();
        List<FailedLink> unmatched = new ArrayList<>();

        for (Element element : bin.getElementsRecursive()) {
            for (Pad src : element.getSrcPads()) {
                // The ghost pads of the bins only pass on the pads of the elements inside
                Pad sink = src instanceof GhostPad ? null : getDownstreamPad(src);
                if (sink == null || sink.hasCurrentCaps()) {
                    continue;
                }

                Caps accepted = sink.queryCaps(null);
                if (src.hasCurrentCaps()) {
                    Caps offered = src.getCurrentCaps();
                    if (!sink.queryAcceptCaps(offered)) {
                        refused.add(new FailedLink("Could not negotiate", src, sink, offered, accepted));
                    }
                } else {
                    Caps offered = src.queryCaps(null);
                    if (!offered.isEmpty() && !accepted.isEmpty() && offered.intersect(accepted).isEmpty()) {
                        unmatched.add(new FailedLink("Could not negotiate", src, sink, offered, accepted));
                    }
                }
            }
        }

        List<FailedLink> failed = refused.isEmpty() ? unmatched : refused;
        return failed.stream()
                .filter(link -> failed.stream().noneMatch(next -> next.src().getParentElement().equals(link.sink().getParentElement())))
                .toList();
    }

    // The pad of the element the given pad sends to, through the ghost pads of the bins in between
    private static Pad getDownstreamPad(Pad pad) {
        Pad peer = pad.getPeer();
        while (peer != null) {
            if (peer instanceof GhostPad ghostPad) {
                // Into a bin
                peer = ghostPad.getTarget();
            } else if (peer.getParent() instanceof GhostPad ghostPad) {
                // Out of a bin, from the inside of its ghost pad
                peer = ghostPad.getPeer();
            } else {
                return peer.getParentElement() == null ? null : peer;
            }
        }
        return null;
    }

    // The fields of the offered format, without which it would be accepted, e.g. "channels", if it was offered with 6
    // and only 2 are accepted. None, if only leaving out more than one would help.
    static List<String> getMismatchingFields(Caps offered, Caps accepted) {
        Set<String> fields = new LinkedHashSet<>();
        for (int i = 0; i < offered.size(); i++) {
            Structure structure = offered.getStructure(i);
            for (int j = 0; j < structure.getFields(); j++) {
                fields.add(structure.getName(j));
            }
        }

        List<String> mismatching = new ArrayList<>();
        for (String field : fields) {
            if (!withoutField(offered, field).intersect(withoutField(accepted, field)).isEmpty()) {
                mismatching.add(field);
            }
        }
        return mismatching;
    }

    private static Caps withoutField(Caps caps, String field) {
        Caps copy = caps.copy();
        for (int i = 0; i < copy.size(); i++) {
            copy.getStructure(i).removeField(field);
        }
        return copy;
    }

    // Without the types, e.g. "audio/x-raw, rate=44100" instead of "audio/x-raw, rate=(int)44100"
    static String toString(Caps caps) {
        if (caps.isEmpty()) {
            return "nothing";
        }
        if (caps.isAny()) {
            return "anything";
        }
        return caps.toString().replaceAll("=\\([A-Za-z0-9_]+\\)", "=");
    }
}
