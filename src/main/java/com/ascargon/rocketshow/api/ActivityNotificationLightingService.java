package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.LightingUniverse;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationLightingService {

    void notifyClients(LightingUniverse lightingUniverse);

}
