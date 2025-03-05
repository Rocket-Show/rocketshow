package com.ascargon.rocketshow.lighting;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface LightingService {

    void reset();

    void send();

    void sendExternalSync(boolean enableMonitor);

    void addLightingUniverse(LightingUniverse lightingUniverse);

    void removeLightingUniverse(LightingUniverse lightingUniverse);

    void setExternalSync(boolean externalSync);

    void close();

    List<OlaPlugin> getOlaPlugins();

    void enablePlugins(List<OlaPlugin> olaPluginList);

    void executeAction(ActionLighting actionLighting);
}
