package com.ascargon.rocketshow.video;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface HdmiService {

    // A display connected to an HDMI output: the connector (e.g. "HDMI-A-1"), its preferred mode (e.g. "1920x1080")
    // and its name from the display itself (EDID), both null if the display doesn't tell
    record ConnectedDisplay(String connector, String mode, String name) {
    }

    // Whether videos should play: true, also when it can't be told whether a display is connected
    boolean isConnected();

    // Unlike isConnected(), false when it can't be told whether a display is connected
    boolean isDisplayConnected();

    List<ConnectedDisplay> getConnectedDisplays();

}
