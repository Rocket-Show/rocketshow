package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.ClockTime;

import static com.ascargon.rocketshow.gstreamer.lowlevel.PbUtilsAPI.PB_UTILS_API;

public class GstDiscoverer {

    // TODO Required?
    public static final String GTYPE_NAME = "GstDiscoverer";

    private Pointer discoverer;
    private Pointer info;

    public GstDiscoverer() {
        discoverer = PB_UTILS_API.gst_discoverer_new(ClockTime.fromSeconds(5), null);
        info = PB_UTILS_API.gst_discoverer_discover_uri(discoverer, "file:///Users/vio/git/RocketShow/target/media/audio/head_smashed_far_away.wav", null);
        ClockTime time = PB_UTILS_API.gst_discoverer_info_get_duration(info);
        System.out.println("AAA: " + time);
    }

}
