package com.ascargon.rocketshow.gstreamer;

import org.springframework.stereotype.Service;

@Service
public interface GstDiscovererService {

    long getDurationMillis(String path) throws Exception;

}
