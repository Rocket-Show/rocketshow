package com.ascargon.rocketshow.gstreamer.lowlevel;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.Clock;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.GError;
import org.freedesktop.gstreamer.lowlevel.GNative;
import org.freedesktop.gstreamer.lowlevel.GTypeMapper;
import org.freedesktop.gstreamer.lowlevel.GstNative;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Lowlevel API in the same style as gstreamer-java for the gst discoverer.
 */
public interface PbUtilsAPI extends Library {

    PbUtilsAPI PB_UTILS_API = GNative.loadLibrary("gstpbutils-1.0", PbUtilsAPI.class,
            new HashMap<String, Object>() {{
                put(Library.OPTION_TYPE_MAPPER, new GTypeMapper());
            }});

    Pointer gst_discoverer_new(ClockTime timeout, GError err);

    Pointer gst_discoverer_discover_uri(Pointer discoverer, String uri, GError err);

    ClockTime gst_discoverer_info_get_duration(Pointer info);

//    public static final class GstElementStruct extends com.sun.jna.Structure {
//
//
//        @Override
//        protected List<String> getFieldOrder() {
//            return Arrays.asList(new String[]{
//                    "object", "state_lock", "state_cond",
//                    "object", "state_lock", "state_cond",
//                    "state_cookie", "target_state", "current_state", "next_state",
//                    "pending_state", "last_return", "bus",
//                    "clock", "base_time", "start_time", "numpads",
//                    "pads", "numsrcpads", "srcpads",
//                    "numsinkpads", "sinkpads", "pads_cookie", "contexts",
//                    "_gst_reserved"
//            });
//        }
//    }

}
