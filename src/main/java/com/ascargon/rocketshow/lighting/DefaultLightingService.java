package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import ola.OlaClient;
import ola.proto.Ola;
import ola.proto.Ola.UniverseInfo;
import ola.proto.Ola.UniverseInfoReply;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DefaultLightingService implements LightingService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultLightingService.class);

    private final CapabilitiesService capabilitiesService;
    private final ActivityNotificationLightingService activityNotificationLightingService;
    private final SettingsService settingsService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    private final String OLA_URL = "http://localhost:9090/";

    // Cache the channel values and send them each time
    private final List<LightingUniverse> lightingUniverseList = new CopyOnWriteArrayList<>();

    private OlaClient olaClient;

    // Delay sending of the universe because of 2 reasons:
    // - Performance: Sending the whole universe each midi event is not fast
    // enough
    // - Glitches: If we send each event separately, you can see the transitions
    // even if they're not meant to be (e.g. activate two channels at the same
    // time, but sent separately)
    private Timer sendUniverseTimer;

    private final HttpClient httpClient;

    private boolean externalSync = false;

    // is OLA initialized and at least one universe prepared?
    private boolean olaReady = false;

    public DefaultLightingService(CapabilitiesService capabilitiesService, ActivityNotificationLightingService activityNotificationLightingService, SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) {
        this.capabilitiesService = capabilitiesService;
        this.activityNotificationLightingService = activityNotificationLightingService;
        this.settingsService = settingsService;
        this.operatingSystemInformationService = operatingSystemInformationService;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(this.operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            try {
                olaClient = new OlaClient();
                capabilitiesService.getCapabilities().setOla(true);
            } catch (Exception e) {
                logger.error("Could not initialize OLA client", e);
            }
        }

        if (!capabilitiesService.getCapabilities().isOla()) {
            return;
        }

        reset();

        initializeUniverses();
    }

    public void reset() {
        if (!capabilitiesService.getCapabilities().isOla()) {
            return;
        }

        // Initialize the universes
        for (LightingUniverse lightingUniverse : lightingUniverseList) {
            HashMap<Integer, Integer> universe = lightingUniverse.getUniverse();

            for (int i = 0; i < 512; i++) {
                universe.put(i, 0);
            }
        }

        sendUniverse(false);
    }

    private void sendUniverse(boolean enableMonitor) {
        logger.trace("Send the lighting universe");

        // Copy the list to protect against changes while mixing
        List<LightingUniverse> lightingUniverseListCopy = new CopyOnWriteArrayList<>(lightingUniverseList);
        List<LightingUniverseMapping> lightingUniverseMappings = getLightingUniverseMappings();

        for (LightingUniverseMapping lightingUniverseMapping : lightingUniverseMappings) {
            if (lightingUniverseMapping.getOlaUniverseId() == null) {
                continue;
            }

            short[] mixedUniverse = mixLightingUniverses(lightingUniverseListCopy, lightingUniverseMapping, lightingUniverseMappings.size() == 1);

            if (olaReady) {
                olaClient.sendDmx(lightingUniverseMapping.getOlaUniverseId(), mixedUniverse);
            }
        }

        if (enableMonitor) {
            notifyMonitor(mixLightingUniverses(lightingUniverseListCopy));
        }
    }

    private List<LightingUniverseMapping> getLightingUniverseMappings() {
        return settingsService.getSettings().getLightingUniverseMappingList();
    }

    private LightingUniverseMapping getDefaultLightingUniverseMapping() {
        List<LightingUniverseMapping> lightingUniverseMappings = getLightingUniverseMappings();
        if (lightingUniverseMappings.isEmpty()) {
            return null;
        }

        return lightingUniverseMappings.getFirst();
    }

    private boolean isDefaultLightingUniverseMapping(LightingUniverseMapping lightingUniverseMapping) {
        LightingUniverseMapping defaultLightingUniverseMapping = getDefaultLightingUniverseMapping();
        return defaultLightingUniverseMapping != null
                && (lightingUniverseMapping == defaultLightingUniverseMapping
                || Objects.equals(lightingUniverseMapping.getUuid(), defaultLightingUniverseMapping.getUuid()));
    }

    private boolean lightingUniverseMatchesMapping(LightingUniverse lightingUniverse, LightingUniverseMapping lightingUniverseMapping, boolean onlyOneMapping) {
        if (onlyOneMapping) {
            return true;
        }

        if (lightingUniverse.getName() == null || lightingUniverse.getName().isBlank()) {
            return isDefaultLightingUniverseMapping(lightingUniverseMapping);
        }

        return lightingUniverse.getName().equals(lightingUniverseMapping.getName());
    }

    private short[] mixLightingUniverses(List<LightingUniverse> sourceLightingUniverses, LightingUniverseMapping lightingUniverseMapping, boolean onlyOneMapping) {
        List<LightingUniverse> matchingLightingUniverses = new ArrayList<>();

        for (LightingUniverse lightingUniverse : sourceLightingUniverses) {
            if (lightingUniverseMatchesMapping(lightingUniverse, lightingUniverseMapping, onlyOneMapping)) {
                matchingLightingUniverses.add(lightingUniverse);
            }
        }

        return mixLightingUniverses(matchingLightingUniverses);
    }

    private short[] mixLightingUniverses(List<LightingUniverse> sourceLightingUniverses) {
        short[] mixedUniverse = new short[512];

        for (int i = 0; i < 512; i++) {
            int highestValue = 0;

            for (LightingUniverse lightingUniverse : sourceLightingUniverses) {
                HashMap<Integer, Integer> universe = lightingUniverse.getUniverse();

                if (universe != null && universe.get(i) != null && universe.get(i) > highestValue) {
                    highestValue = universe.get(i);
                }
            }

            mixedUniverse[i] = (short) highestValue;
        }

        return mixedUniverse;
    }

    private void notifyMonitor(short[] mixedUniverse) {
        HashMap<Integer, Integer> mixedActivityUniverse = new HashMap<>();
        for (int i = 0; i < 512; i++) {
            mixedActivityUniverse.put(i, (int) mixedUniverse[i]);
        }
        LightingUniverse activityUniverse = new LightingUniverse();
        activityUniverse.setUniverse(mixedActivityUniverse);
        activityNotificationLightingService.notifyClients(activityUniverse);
    }

    // Make sure, this method is synchronized. Otherwise it may happen, that
    // some timers are started in parallel, because different threads send at
    // the same time. This will cause the OLA rpc stream to break and a restart
    // is required.
    public synchronized void send() {
        logger.trace("Sending a lighting value");

        if (externalSync) {
            // Don't manage the send frequency internally, but rely on an external handler
            return;
        }

        // Schedule the specified count of executions in the specified delay
        if (sendUniverseTimer != null) {
            // There is already a timer running -> let it finish
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Send the universe
                    sendUniverse(settingsService.getSettings().getEnableMonitor());
                } catch (Exception e) {
                    logger.error("Could not send the lighting universe", e);
                }

                if (sendUniverseTimer != null) {
                    sendUniverseTimer.cancel();
                }

                sendUniverseTimer = null;
            }
        };

        sendUniverseTimer = new Timer();
        sendUniverseTimer.schedule(timerTask, settingsService.getSettings().getLightingSendDelayMillis());
    }

    @Override
    public void sendExternalSync(boolean enableMonitor) {
        if (!externalSync) {
            logger.debug("Cannot send external sync, because it is not enabled");
            return;
        }

        try {
            // Send the universe immediately
            sendUniverse(enableMonitor);
        } catch (Exception e) {
            logger.error("Could not send the lighting universe", e);
        }
    }

    private void createOlaUniverse(LightingUniverseMapping lightingUniverseMapping) throws IOException {
        logger.debug("Adding new universe '{}' with port '{}'...", lightingUniverseMapping.getOlaUniverseId(), lightingUniverseMapping.getOlaOutputPortId());

        HttpClient httpClient;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        HttpPost httpPost = new HttpPost(OLA_URL + "new_universe");

        List<NameValuePair> data = new ArrayList<>(3);

        data.add(new BasicNameValuePair("id", String.valueOf(lightingUniverseMapping.getOlaUniverseId())));
        data.add(new BasicNameValuePair("name", lightingUniverseMapping.getName()));
        if (lightingUniverseMapping.getOlaOutputPortId() != null && !lightingUniverseMapping.getOlaOutputPortId().isBlank()) {
            data.add(new BasicNameValuePair("add_ports", lightingUniverseMapping.getOlaOutputPortId()));
        }

        httpPost.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");

        HttpResponse response = httpClient.execute(httpPost);

        // Read the response. The POST connection will not be released otherwise
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        String line;

        while ((line = bufferedReader.readLine()) != null) {
            logger.debug("Response from OLA POST: " + line);
        }
    }

    private void addOlaUniversePort(LightingUniverseMapping lightingUniverseMapping) throws IOException {
        modifyOlaUniversePorts(
                lightingUniverseMapping.getOlaUniverseId(),
                getOlaUniverseName(lightingUniverseMapping),
                "add_ports",
                lightingUniverseMapping.getOlaOutputPortId(),
                "Adding"
        );
    }

    private void modifyOlaUniversePorts(Integer olaUniverseId, String name, String portParameter, String portIds, String logAction) throws IOException {
        if (olaUniverseId == null
                || portIds == null
                || portIds.isBlank()) {
            return;
        }

        if (!olaUniverseExists(olaUniverseId)) {
            return;
        }

        logger.debug("{} port '{}' from universe '{}'...", logAction, portIds, olaUniverseId);

        HttpPost httpPost = new HttpPost(OLA_URL + "modify_universe");

        List<NameValuePair> data = new ArrayList<>(3);
        data.add(new BasicNameValuePair("id", String.valueOf(olaUniverseId)));
        data.add(new BasicNameValuePair("name", name));
        data.add(new BasicNameValuePair(portParameter, portIds));

        httpPost.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");

        HttpResponse response = httpClient.execute(httpPost);

        // Read the response. The POST connection will not be released otherwise
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        String line;

        while ((line = bufferedReader.readLine()) != null) {
            logger.debug("Response from OLA POST: " + line);
        }
    }

    private String getOlaUniverseName(LightingUniverseMapping lightingUniverseMapping) {
        if (lightingUniverseMapping.getName() != null && !lightingUniverseMapping.getName().isBlank()) {
            return lightingUniverseMapping.getName();
        }

        return "Universe " + lightingUniverseMapping.getOlaUniverseId();
    }

    private boolean olaUniverseExists(int universeId) {
        UniverseInfoReply universeInfoReply = olaClient.getUniverseList();

        if (universeInfoReply == null) {
            return false;
        }

        for (UniverseInfo universeInfo : universeInfoReply.getUniverseList()) {
            if (universeInfo.getUniverse() == universeId) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void initializeUniverses() {
        updateUniverses(getLightingUniverseMappings());
    }

    @Override
    public void updateUniverses(List<LightingUniverseMapping> lightingUniverseMappings) {
        if (olaClient == null) {
            // OLA client is not connected
            return;
        }

        logger.debug("Initializing lighting universes on OLA...");

        removeAllOlaPorts(lightingUniverseMappings);
        olaReady = false;

        for (LightingUniverseMapping lightingUniverseMapping : lightingUniverseMappings) {
            if (lightingUniverseMapping.getOlaUniverseId() == null) {
                logger.warn("Skipping lighting universe '{}' because no OLA universe id is configured", lightingUniverseMapping.getName());
                continue;
            }

            if (lightingUniverseMapping.getOlaOutputPortId() == null || lightingUniverseMapping.getOlaOutputPortId().isBlank()) {
                logger.trace("No OLA output port configured for universe '{}'", lightingUniverseMapping.getName());
                continue;
            }

            try {
                createOrUpdateOlaUniverse(lightingUniverseMapping);
                olaReady = true;
            } catch (Exception e) {
                logger.error("Could not create or update universe on OLA", e);
            }
        }

        logger.debug("Lighting universes on OLA initialized");
    }

    private void createOrUpdateOlaUniverse(LightingUniverseMapping lightingUniverseMapping) throws IOException {
        if (olaUniverseExists(lightingUniverseMapping.getOlaUniverseId())) {
            olaClient.setUniverseName(lightingUniverseMapping.getOlaUniverseId(), lightingUniverseMapping.getName());
            addOlaUniversePort(lightingUniverseMapping);
            return;
        }

        createOlaUniverse(lightingUniverseMapping);
        olaClient.setUniverseName(lightingUniverseMapping.getOlaUniverseId(), lightingUniverseMapping.getName());
    }

    private void removeAllOlaPorts(List<LightingUniverseMapping> newLightingUniverseMappings) {
        Ola.DeviceInfoReply deviceInfoReply = olaClient.getDeviceInfo();
        Map<Integer, Set<String>> portIdsByUniverse = new HashMap<>();

        if (deviceInfoReply != null) {
            for (Ola.DeviceInfo deviceInfo : deviceInfoReply.getDeviceList()) {
                if (!deviceInfo.hasDeviceAlias()) {
                    continue;
                }

                for (Ola.PortInfo inputPort : deviceInfo.getInputPortList()) {
                    addOlaPortId(portIdsByUniverse, inputPort, deviceInfo.getDeviceAlias(), "I");
                }

                for (Ola.PortInfo outputPort : deviceInfo.getOutputPortList()) {
                    addOlaPortId(portIdsByUniverse, outputPort, deviceInfo.getDeviceAlias(), "O");
                }
            }
        }

        addConfiguredOlaPortIds(portIdsByUniverse, getLightingUniverseMappings());
        addConfiguredOlaPortIds(portIdsByUniverse, newLightingUniverseMappings);

        for (Map.Entry<Integer, Set<String>> portIdsByUniverseEntry : portIdsByUniverse.entrySet()) {
            try {
                modifyOlaUniversePorts(
                        portIdsByUniverseEntry.getKey(),
                        getOlaUniverseName(portIdsByUniverseEntry.getKey()),
                        "remove_ports",
                        String.join(",", portIdsByUniverseEntry.getValue()),
                        "Removing"
                );
            } catch (Exception e) {
                logger.error("Could not remove OLA ports from universe '{}'", portIdsByUniverseEntry.getKey(), e);
            }
        }
    }

    private void addOlaPortId(Map<Integer, Set<String>> portIdsByUniverse, Ola.PortInfo portInfo, int deviceAlias, String direction) {
        if (!portInfo.hasUniverse() || !portInfo.hasPortId()) {
            return;
        }

        addOlaPortId(portIdsByUniverse, portInfo.getUniverse(), deviceAlias + "-" + direction + "-" + portInfo.getPortId());
    }

    private void addConfiguredOlaPortIds(Map<Integer, Set<String>> portIdsByUniverse, List<LightingUniverseMapping> lightingUniverseMappings) {
        for (LightingUniverseMapping lightingUniverseMapping : lightingUniverseMappings) {
            if (lightingUniverseMapping.getOlaUniverseId() == null
                    || lightingUniverseMapping.getOlaOutputPortId() == null
                    || lightingUniverseMapping.getOlaOutputPortId().isBlank()) {
                continue;
            }

            addOlaPortId(portIdsByUniverse, lightingUniverseMapping.getOlaUniverseId(), lightingUniverseMapping.getOlaOutputPortId());
        }
    }

    private void addOlaPortId(Map<Integer, Set<String>> portIdsByUniverse, int universeId, String portId) {
        Set<String> portIds = portIdsByUniverse.computeIfAbsent(universeId, id -> new LinkedHashSet<>());
        portIds.add(portId);
    }

    private String getOlaUniverseName(int universeId) {
        UniverseInfoReply universeInfoReply = olaClient.getUniverseInfo(universeId);

        if (universeInfoReply == null) {
            return "Universe " + universeId;
        }

        for (UniverseInfo universeInfo : universeInfoReply.getUniverseList()) {
            if (universeInfo.getUniverse() == universeId && universeInfo.hasName() && !universeInfo.getName().isBlank()) {
                return universeInfo.getName();
            }
        }

        return "Universe " + universeId;
    }

    @Override
    public void addLightingUniverse(LightingUniverse lightingUniverse) {
        lightingUniverseList.add(lightingUniverse);
    }

    @Override
    public void removeLightingUniverse(LightingUniverse lightingUniverse) {
        lightingUniverseList.remove(lightingUniverse);
    }

    @Override
    public void setExternalSync(boolean externalSync) {
        this.externalSync = externalSync;
    }

    @Override
    @PreDestroy
    public void close() {
        if (sendUniverseTimer != null) {
            sendUniverseTimer.cancel();
            sendUniverseTimer = null;
        }

        reset();
    }

    private OlaPlugin createOlaPluginFromInfo(Ola.PluginInfo pluginInfo) {
        OlaPlugin olaPlugin = new OlaPlugin();
        olaPlugin.setId(pluginInfo.getPluginId());
        olaPlugin.setName(pluginInfo.getName());
        return olaPlugin;
    }

    @Override
    public List<OlaPlugin> getOlaPlugins() {
        List<OlaPlugin> olaPluginList = new ArrayList<>();

        if (olaClient != null) {
            List<Ola.PluginInfo> pluginInfoList = olaClient.getPlugins().getPluginList();
            for (Ola.PluginInfo pluginInfo : pluginInfoList) {
                OlaPlugin olaPlugin = createOlaPluginFromInfo(pluginInfo);
                olaPluginList.add(olaPlugin);

                Ola.PluginStateReply pluginStateReply = olaClient.getPluginState(olaPlugin.getId());

                if (pluginStateReply != null) {
                    for (Ola.PluginInfo conflictingPluginInfo : pluginStateReply.getConflictsWithList()) {
                        olaPlugin.getConflictList().add(createOlaPluginFromInfo(conflictingPluginInfo));
                    }
                }
            }
        }

        return olaPluginList;
    }

    @Override
    public List<OlaPort> getOlaOutputPorts() {
        List<OlaPort> olaOutputPorts = new ArrayList<>();

        if (olaClient == null) {
            return olaOutputPorts;
        }

        try {
            // Query the OLA JSON API for all ports. The JSON endpoint exposes the string port id
            // format that OLA's new_universe endpoint expects in add_ports.
            HttpGet httpGet = new HttpGet(OLA_URL + "json/get_ports");
            HttpResponse response = httpClient.execute(httpGet);

            ObjectMapper mapper = new ObjectMapper();
            OlaPort[] olaPortList = mapper.readValue(response.getEntity().getContent(), OlaPort[].class);

            for (OlaPort olaPort : olaPortList) {
                if (olaPort.isOutput() && olaPort.getId() != null && !olaPort.getId().isBlank()) {
                    olaOutputPorts.add(olaPort);
                }
            }

            addConfiguredOlaOutputPorts(olaOutputPorts);
        } catch (Exception e) {
            logger.error("Could not get OLA output ports", e);
        }

        return olaOutputPorts;
    }

    private void addConfiguredOlaOutputPorts(List<OlaPort> olaOutputPorts) {
        for (LightingUniverseMapping lightingUniverseMapping : getLightingUniverseMappings()) {
            if (lightingUniverseMapping.getOlaOutputPortId() == null
                    || lightingUniverseMapping.getOlaOutputPortId().isBlank()
                    || containsOlaOutputPort(olaOutputPorts, lightingUniverseMapping.getOlaOutputPortId())
                    || lightingUniverseMapping.getOlaUniverseId() == null) {
                continue;
            }

            UniverseInfoReply universeInfoReply = olaClient.getUniverseInfo(lightingUniverseMapping.getOlaUniverseId());
            String description = getOutputPortDescription(universeInfoReply);

            if (description == null || description.isBlank()) {
                continue;
            }

            OlaPort olaPort = new OlaPort();
            olaPort.setId(lightingUniverseMapping.getOlaOutputPortId());
            olaPort.setDevice(description);
            olaPort.setOutput(true);
            olaOutputPorts.add(olaPort);
        }
    }

    private boolean containsOlaOutputPort(List<OlaPort> olaOutputPorts, String olaOutputPortId) {
        return olaOutputPorts.stream().anyMatch(olaPort -> olaOutputPortId.equals(olaPort.getId()));
    }

    private String getOutputPortDescription(UniverseInfoReply universeInfoReply) {
        if (universeInfoReply == null) {
            return null;
        }

        List<String> descriptions = new ArrayList<>();
        for (UniverseInfo universeInfo : universeInfoReply.getUniverseList()) {
            for (Ola.PortInfo outputPort : universeInfo.getOutputPortsList()) {
                if (outputPort.hasDescription() && !outputPort.getDescription().isBlank()) {
                    descriptions.add(outputPort.getDescription());
                }
            }
        }

        if (descriptions.isEmpty()) {
            return null;
        }

        return String.join(", ", descriptions);
    }

    @Override
    public void enablePlugins(List<OlaPlugin> olaPluginList) {
        // Disable all plugins, except the one to be enabled
        for (OlaPlugin olaPlugin : getOlaPlugins()) {
            boolean enabled = olaPluginList.stream().anyMatch(plugin -> plugin.getName().equals(olaPlugin.getName()));
            olaClient.setPluginState(olaPlugin.getId(), enabled);
        }
    }

    private LightingUniverse getLightingUniverseForAction(String universeName) {
        if (!hasLightingUniverseMapping(universeName)) {
            logger.warn("Skipping lighting action for unknown universe '{}'", universeName);
            return null;
        }

        for (LightingUniverse lightingUniverse : lightingUniverseList) {
            if (Objects.equals(lightingUniverse.getName(), universeName)) {
                return lightingUniverse;
            }
        }

        LightingUniverse lightingUniverse = new LightingUniverse();
        lightingUniverse.setName(universeName);
        lightingUniverseList.add(lightingUniverse);
        return lightingUniverse;
    }

    private boolean hasLightingUniverseMapping(String universeName) {
        if (universeName == null || universeName.isBlank()) {
            return false;
        }

        for (LightingUniverseMapping lightingUniverseMapping : getLightingUniverseMappings()) {
            if (Objects.equals(lightingUniverseMapping.getName(), universeName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void executeAction(ActionLighting actionLighting) {
        for (LightingActionUniverse lightingActionUniverse : actionLighting.getLightingActionUniverseList()) {
            LightingUniverse lightingUniverse = getLightingUniverseForAction(lightingActionUniverse.getUniverseName());
            if (lightingUniverse == null) {
                continue;
            }

            for (LightingActionChannelValue channelValue : lightingActionUniverse.getChannelValueList()) {
                lightingUniverse.getUniverse().put(channelValue.getChannel(), channelValue.getValue());
            }
        }

        send();
    }

}
