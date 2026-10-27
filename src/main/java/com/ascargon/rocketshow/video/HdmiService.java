package com.ascargon.rocketshow.video;

import org.springframework.stereotype.Service;

@Service
public interface HdmiService {

    boolean isConnected();

    // Unlike isConnected(), false when it can't be told whether a display is connected
    boolean isDisplayConnected();

}
