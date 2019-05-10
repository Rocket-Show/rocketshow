package com.ascargon.rocketshow.lighting;

import org.springframework.stereotype.Service;

@Service
public interface LightingService {

    void reset();

    void send();

    void sendExternalSync();

    void addLightingUniverse(LightingUniverse lightingUniverse);

    void removeLightingUniverse(LightingUniverse lightingUniverse);

    void setExternalSync(boolean externalSync);

    void close();

}
