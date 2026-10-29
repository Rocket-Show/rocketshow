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

    // Whether OLA has patched a universe of the settings to its output port, e.g. for the self-test
    boolean isPatched(LightingUniverse lightingUniverse);

    // Sends the values to a universe of the settings right away, without the values of the compositions, e.g. for the
    // self-test. False when OLA didn't take them.
    boolean sendDmx(LightingUniverse lightingUniverse, short[] values);
}
