package com.ascargon.rocketshow.lighting;

import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationLightingService {

    void notifyClients(LightingUniverseState lightingUniverse);

}
