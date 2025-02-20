package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.lighting.LightingUniverse;
import org.springframework.stereotype.Service;

@Service
public interface ActivityNotificationLightingService {

    void notifyClients(LightingUniverse lightingUniverse);

}
