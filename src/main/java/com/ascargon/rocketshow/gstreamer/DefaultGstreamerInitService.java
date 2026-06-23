package com.ascargon.rocketshow.gstreamer;

import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.sun.jna.Platform;
import org.freedesktop.gstreamer.Gst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class DefaultGstreamerInitService implements GstreamerInitService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultGstreamerInitService.class);

    public DefaultGstreamerInitService(CapabilitiesService capabilitiesService) {
        try {
            // Setup the Gstreamer paths
            if (Platform.isMac()) {
                // libs path
                String gstPath = System.getProperty("gstreamer.path", "/opt/homebrew/lib");

                if (!gstPath.isEmpty()) {
                    String jnaPath = System.getProperty("jna.library.path", "").trim();
                    if (jnaPath.isEmpty()) {
                        System.setProperty("jna.library.path", gstPath);
                    } else {
                        System.setProperty("jna.library.path", jnaPath + File.pathSeparator + gstPath);
                    }
                }
            }

            Gst.init();
        } catch (Exception | UnsatisfiedLinkError e) {
            // Gstreamer might not be installed properly or not be installed at all
            logger.error("Could not initialize Gstreamer", e);
            capabilitiesService.getCapabilities().setGstreamer(false);
        }
    }

}
