package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Pointer;
import org.springframework.stereotype.Service;

@Service
public interface GstDiscovererService {

    Pointer getDiscovererInformation(String path) throws Exception;

    long getDurationMillis(Pointer discovererInformation);

    int getChannels(Pointer discovererInformation);

}
