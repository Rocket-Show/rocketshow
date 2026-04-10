package com.ascargon.rocketshow.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DefaultHdmiService implements HdmiService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHdmiService.class);

    private static final Path HDMI_STATUS_PATH = Path.of("/sys/class/drm/card1-HDMI-A-1/status");

    @Override
    public boolean isConnected() {
        try {
            String status = Files.readString(HDMI_STATUS_PATH).trim();
            logger.debug("HDMI status: {}", status);
            return "connected".equalsIgnoreCase(status);
        } catch (Exception e) {
            logger.error("Could not read HDMI status from {}", HDMI_STATUS_PATH, e);

            // Assume hdmi is connected by default, to output video if possible
            return true;
        }
    }
}