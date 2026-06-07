package com.ascargon.rocketshow.lighting;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface LightingService {

    void reset();

    void send();

    void sendExternalSync(boolean enableMonitor);

    void addLightingUniverse(LightingUniverseState lightingUniverse);

    void removeLightingUniverse(LightingUniverseState lightingUniverse);

    void setExternalSync(boolean externalSync);

    void close();

    List<OlaPlugin> getOlaPlugins();

    List<OlaPort> getOlaOutputPorts();

    void enablePlugins(List<OlaPlugin> olaPluginList);

    void reloadOlaPlugins();

    void initializeUniverses();

    void updateUniverses(List<LightingUniverse> lightingUniverses);

    void executeAction(ActionLighting actionLighting);
}
