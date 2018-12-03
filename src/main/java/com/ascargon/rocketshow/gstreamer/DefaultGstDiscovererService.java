package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.lowlevel.GstAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.ascargon.rocketshow.gstreamer.PbUtilsAPI.PB_UTILS_API;

@Service
public class DefaultGstDiscovererService implements GstDiscovererService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultGstDiscovererService.class);

    private Pointer discoverer = null;
    private Pointer info;

    @Override
    public synchronized long getDurationMillis(String path) throws Exception {
        if (discoverer == null) {
            discoverer = PB_UTILS_API.gst_discoverer_new(ClockTime.fromSeconds(5), null);
        }

        GstAPI.GErrorStruct error = new GstAPI.GErrorStruct();

        info = PB_UTILS_API.gst_discoverer_discover_uri(discoverer, "file://" + path, error);

        if (PB_UTILS_API.gst_discoverer_info_get_result(info) != PbUtilsAPI.GstDiscovererResult.GST_DISCOVERER_OK) {
            // Unfortunately, error.message is always null. Don't know why. And
            // getting the message from domain and code also does not work.

            throw new Exception("Could not discover file " + path + ". Result: " + PB_UTILS_API.gst_discoverer_info_get_result(info).toString());
        }

        ClockTime time = PB_UTILS_API.gst_discoverer_info_get_duration(info);

        return time.toMillis();
    }

}
