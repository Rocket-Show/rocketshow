package com.ascargon.rocketshow.settings;

import org.springframework.stereotype.Service;

@Service
public class DefaultCapabilitiesService implements CapabilitiesService {

    private final Capabilities capabilities = new Capabilities();

    @Override
    public Capabilities getCapabilities() {
        return capabilities;
    }

}
