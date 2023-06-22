package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.lowlevel.GlibAPI;
import org.freedesktop.gstreamer.lowlevel.GstAPI;
import org.freedesktop.gstreamer.lowlevel.GstClockAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.ascargon.rocketshow.gstreamer.PbUtilsApi.PB_UTILS_API;
import static org.freedesktop.gstreamer.lowlevel.GstPluginAPI.GSTPLUGIN_API;

@Service
public class DefaultGstDiscovererService implements GstDiscovererService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultGstDiscovererService.class);

    private Pointer discoverer = null;

    @Override
    public synchronized Pointer getDiscovererInformation(String path) throws Exception {
        if (discoverer == null) {
            discoverer = PB_UTILS_API.gst_discoverer_new(ClockTime.fromSeconds(5), null);
        }

        GstAPI.GErrorStruct error = new GstAPI.GErrorStruct();

        Pointer discovererInformation = PB_UTILS_API.gst_discoverer_discover_uri(discoverer, "file://" + path, error);

        // timing out is fine most of the time, because the required info is still found
        if (PB_UTILS_API.gst_discoverer_info_get_result(discovererInformation)
                != PbUtilsApi.GstDiscovererResult.GST_DISCOVERER_OK
                && PB_UTILS_API.gst_discoverer_info_get_result(discovererInformation)
                != PbUtilsApi.GstDiscovererResult.GST_DISCOVERER_TIMEOUT
        ) {
            // Unfortunately, error.message is always null. Don't know why. And
            // getting the message from domain and code also does not work.

            throw new Exception("Could not get media information for file " + path + ". Result: " + PB_UTILS_API.gst_discoverer_info_get_result(discovererInformation).toString());
        }

        return discovererInformation;
    }

    @Override
    public long getDurationMillis(Pointer discovererInformation) {
        return PB_UTILS_API.gst_discoverer_info_get_duration(discovererInformation) / 1000000;
    }

    @Override
    public int getChannels(Pointer discovererInformation) {
        // Get a list of GstDiscovererStreamInfo out of the discoverer information
        GlibAPI.GList gList = PB_UTILS_API.gst_discoverer_info_get_audio_streams(discovererInformation);

        int channels = 0;


        GlibAPI.GList next = gList;
        while (next != null) {
            if (next.data != null) {
                channels += PB_UTILS_API.gst_discoverer_audio_info_get_channels(next.data);
            }
            next = next.next();
        }

        GSTPLUGIN_API.gst_plugin_list_free(gList);

        logger.trace("Found channels: " + channels);
        return channels;
    }

}
