package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.GError;
import org.freedesktop.gstreamer.lowlevel.GNative;
import org.freedesktop.gstreamer.lowlevel.GTypeMapper;
import org.freedesktop.gstreamer.lowlevel.GstAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * JNA API for the Gstreamer pbutils library.
 */
public interface PbUtilsAPI extends Library {

    PbUtilsAPI PB_UTILS_API = GNative.loadLibrary("gstpbutils-1.0", PbUtilsAPI.class,
            new HashMap<String, Object>() {{
                put(Library.OPTION_TYPE_MAPPER, new GTypeMapper());
            }});

    enum GstDiscovererResult {
        GST_DISCOVERER_OK,
        GST_DISCOVERER_URI_INVALID,
        GST_DISCOVERER_ERROR,
        GST_DISCOVERER_TIMEOUT,
        GST_DISCOVERER_BUSY,
        GST_DISCOVERER_MISSING_PLUGINS
    };

    Pointer gst_discoverer_new(ClockTime timeout, GstAPI.GErrorStruct error);

    Pointer gst_discoverer_discover_uri(Pointer discoverer, String uri, GstAPI.GErrorStruct error);

    GstDiscovererResult gst_discoverer_info_get_result (Pointer discoverer);

    ClockTime gst_discoverer_info_get_duration(Pointer info);

}
