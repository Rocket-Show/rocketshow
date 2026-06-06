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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private final List<LightingUniverseState> lightingUniverseList = new CopyOnWriteArrayList<>();

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

    private ScheduledExecutorService olaRetryExecutor;
    private static final int OLA_MAX_RETRIES = 10;
    private static final long OLA_RETRY_DELAY_MS = 3000;

    public DefaultLightingService(CapabilitiesService capabilitiesService, ActivityNotificationLightingService activityNotificationLightingService, SettingsService settingsService, OperatingSystemInformationService operatingSystemInformationService) {
        this.capabilitiesService = capabilitiesService;
        this.activityNotificationLightingService = activityNotificationLightingService;
        this.settingsService = settingsService;
        this.operatingSystemInformationService = operatingSystemInformationService;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        if (OperatingSystemInformation.SubType.RASPBERRYOS.equals(this.operatingSystemInformationService.getOperatingSystemInformation().getSubType())) {
            olaRetryExecutor = Executors.newSingleThreadScheduledExecutor();
            tryInitializeOla(1);
        }
    }

    private void tryInitializeOla(int attempt) {
        try {
            if (olaClient == null) {
                olaClient = new OlaClient();
                capabilitiesService.getCapabilities().setOla(true);
                reset();
            }
            reconcileOlaPortIds();
            initializeUniverses();
        } catch (Exception e) {
            logger.error("Could not initialize OLA client (attempt {}/{})", attempt, OLA_MAX_RETRIES, e);
        }

        if (!olaReady && attempt < OLA_MAX_RETRIES) {
            logger.warn("OLA not ready, retrying in {}ms (attempt {}/{})", OLA_RETRY_DELAY_MS, attempt, OLA_MAX_RETRIES);
            olaRetryExecutor.schedule(() -> tryInitializeOla(attempt + 1), OLA_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        } else if (olaReady) {
            olaRetryExecutor.shutdown();
        }
    }

    private List<OlaPort> fetchLiveOlaOutputPorts() throws Exception {
        HttpGet httpGet = new HttpGet(OLA_URL + "json/get_ports");
        HttpResponse response = httpClient.execute(httpGet);
        ObjectMapper mapper = new ObjectMapper();
        OlaPort[] portArray = mapper.readValue(response.getEntity().getContent(), OlaPort[].class);
        List<OlaPort> result = new ArrayList<>();
        for (OlaPort port : portArray) {
            if (port.isOutput() && port.getId() != null && !port.getId().isBlank()) {
                result.add(port);
            }
        }
        return result;
    }

    private void reconcileOlaPortIds() {
        List<LightingUniverse> lightingUniverses = getLightingUniverses();
        if (lightingUniverses.isEmpty()) {
            logger.debug("OLA port reconciliation: no configured universes, skipping");
            return;
        }

        // OLA's json/get_ports only lists ports that are not currently bound to a universe.
        // Remove all port assignments first so every port becomes visible for matching.
        // initializeUniverses() will re-bind them afterwards with the corrected port IDs.
        logger.debug("OLA port reconciliation: removing all OLA port assignments before querying live ports");
        removeAllOlaPorts(lightingUniverses);

        List<OlaPort> livePorts;
        try {
            livePorts = fetchLiveOlaOutputPorts();
        } catch (Exception e) {
            logger.warn("Could not fetch OLA ports for port ID reconciliation", e);
            return;
        }

        if (livePorts.isEmpty()) {
            logger.warn("OLA port reconciliation: no live output ports returned by OLA");
        } else {
            logger.debug("OLA port reconciliation: {} live output port(s) available:", livePorts.size());
            for (OlaPort port : livePorts) {
                logger.debug("  id='{}' device='{}'", port.getId(), port.getDevice());
            }
        }

        boolean updated = false;
        for (LightingUniverse universe : lightingUniverses) {
            if (universe.getOlaOutputPortDevice() != null && !universe.getOlaOutputPortDevice().isBlank()) {
                // Match by device name → update port ID if it changed
                boolean found = false;
                for (OlaPort port : livePorts) {
                    if (universe.getOlaOutputPortDevice().equals(port.getDevice())) {
                        found = true;
                        if (!port.getId().equals(universe.getOlaOutputPortId())) {
                            logger.info("Updating port ID for universe '{}' from '{}' to '{}' (device: '{}')",
                                    universe.getName(), universe.getOlaOutputPortId(), port.getId(), port.getDevice());
                            universe.setOlaOutputPortId(port.getId());
                            updated = true;
                        } else {
                            logger.debug("Universe '{}': port ID '{}' still matches device '{}', no update needed",
                                    universe.getName(), universe.getOlaOutputPortId(), universe.getOlaOutputPortDevice());
                        }
                        break;
                    }
                }
                if (!found) {
                    logger.warn("OLA port reconciliation: no live port found for universe '{}' with device '{}' (current port ID: '{}'). Available devices: {}",
                            universe.getName(),
                            universe.getOlaOutputPortDevice(),
                            universe.getOlaOutputPortId(),
                            livePorts.stream().map(OlaPort::getDevice).toList());
                }
            } else if (universe.getOlaOutputPortId() != null && !universe.getOlaOutputPortId().isBlank()) {
                // Backfill: store device name for existing universes that don't have it yet
                boolean found = false;
                for (OlaPort port : livePorts) {
                    if (universe.getOlaOutputPortId().equals(port.getId())) {
                        found = true;
                        if (port.getDevice() != null && !port.getDevice().isBlank()) {
                            logger.info("Storing device '{}' for universe '{}' (port ID: '{}')",
                                    port.getDevice(), universe.getName(), port.getId());
                            universe.setOlaOutputPortDevice(port.getDevice());
                            updated = true;
                        } else {
                            logger.debug("Universe '{}': live port '{}' has no device name, skipping backfill",
                                    universe.getName(), port.getId());
                        }
                        break;
                    }
                }
                if (!found) {
                    logger.warn("OLA port reconciliation: no live port found for universe '{}' with port ID '{}' (no device stored yet). Available port IDs: {}",
                            universe.getName(),
                            universe.getOlaOutputPortId(),
                            livePorts.stream().map(OlaPort::getId).toList());
                }
            } else {
                logger.debug("Universe '{}': no port ID or device configured, skipping", universe.getName());
            }
        }

        if (updated) {
            try {
                settingsService.save();
                logger.debug("Settings saved after OLA port ID reconciliation");
            } catch (Exception e) {
                logger.error("Could not save settings after OLA port ID reconciliation", e);
            }
        } else {
            logger.debug("OLA port reconciliation: no changes needed");
        }
    }

    public void reset() {
        if (!capabilitiesService.getCapabilities().isOla()) {
            return;
        }

        // Initialize the universes
        for (LightingUniverseState lightingUniverse : lightingUniverseList) {
            HashMap<Integer, Integer> universe = lightingUniverse.getUniverse();

            for (int i = 0; i < 512; i++) {
                universe.put(i, 0);
            }
        }

        sendUniverse(false);
    }

    private synchronized void sendUniverse(boolean enableMonitor) {
        logger.trace("Send the lighting universe");

        // Copy the list to protect against changes while mixing
        List<LightingUniverseState> lightingUniverseListCopy = new CopyOnWriteArrayList<>(lightingUniverseList);
        List<LightingUniverse> lightingUniverses = getLightingUniverses();

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            if (lightingUniverse.getOlaUniverseId() == null) {
                continue;
            }

            short[] mixedUniverse = mixLightingUniverses(lightingUniverseListCopy, lightingUniverse, lightingUniverses.size() == 1);

            if (olaReady) {
                olaClient.sendDmx(lightingUniverse.getOlaUniverseId(), mixedUniverse);
            }
        }

        if (enableMonitor) {
            notifyMonitor(mixLightingUniverses(lightingUniverseListCopy));
        }
    }

    private List<LightingUniverse> getLightingUniverses() {
        return settingsService.getSettings().getLightingUniverseList();
    }

    private LightingUniverse getDefaultLightingUniverse() {
        List<LightingUniverse> lightingUniverses = getLightingUniverses();
        if (lightingUniverses.isEmpty()) {
            return null;
        }

        return lightingUniverses.getFirst();
    }

    private boolean isDefaultLightingUniverse(LightingUniverse lightingUniverse) {
        LightingUniverse defaultLightingUniverse = getDefaultLightingUniverse();
        return defaultLightingUniverse != null
                && (lightingUniverse == defaultLightingUniverse
                || Objects.equals(lightingUniverse.getUuid(), defaultLightingUniverse.getUuid()));
    }

    private boolean lightingUniverseMatchesMapping(LightingUniverseState lightingUniverseState, LightingUniverse lightingUniverse, boolean onlyOneMapping) {
        if (lightingUniverseState.getMappingUuid() != null && !lightingUniverseState.getMappingUuid().isBlank()) {
            return Objects.equals(lightingUniverseState.getMappingUuid(), lightingUniverse.getUuid());
        }

        if (onlyOneMapping) {
            return true;
        }

        if (lightingUniverseState.getName() == null || lightingUniverseState.getName().isBlank()) {
            return isDefaultLightingUniverse(lightingUniverse);
        }

        return lightingUniverseState.getName().equals(lightingUniverse.getName());
    }

    private short[] mixLightingUniverses(List<LightingUniverseState> sourceLightingUniverses, LightingUniverse lightingUniverse, boolean onlyOneMapping) {
        List<LightingUniverseState> matchingLightingUniverses = new ArrayList<>();

        for (LightingUniverseState lightingUniverseState : sourceLightingUniverses) {
            if (lightingUniverseMatchesMapping(lightingUniverseState, lightingUniverse, onlyOneMapping)) {
                matchingLightingUniverses.add(lightingUniverseState);
            }
        }

        return mixLightingUniverses(matchingLightingUniverses);
    }

    private short[] mixLightingUniverses(List<LightingUniverseState> sourceLightingUniverses) {
        short[] mixedUniverse = new short[512];

        for (int i = 0; i < 512; i++) {
            int highestValue = 0;

            for (LightingUniverseState lightingUniverse : sourceLightingUniverses) {
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
        LightingUniverseState activityUniverse = new LightingUniverseState();
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

    private void createOlaUniverse(LightingUniverse lightingUniverse) throws IOException {
        logger.debug("Adding new universe '{}' with port '{}'...", lightingUniverse.getOlaUniverseId(), lightingUniverse.getOlaOutputPortId());

        HttpClient httpClient;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        HttpPost httpPost = new HttpPost(OLA_URL + "new_universe");

        List<NameValuePair> data = new ArrayList<>(3);

        data.add(new BasicNameValuePair("id", String.valueOf(lightingUniverse.getOlaUniverseId())));
        data.add(new BasicNameValuePair("name", lightingUniverse.getName()));
        if (lightingUniverse.getOlaOutputPortId() != null && !lightingUniverse.getOlaOutputPortId().isBlank()) {
            data.add(new BasicNameValuePair("add_ports", lightingUniverse.getOlaOutputPortId()));
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

    private void addOlaUniversePort(LightingUniverse lightingUniverse) throws IOException {
        modifyOlaUniversePorts(
                lightingUniverse.getOlaUniverseId(),
                getOlaUniverseName(lightingUniverse),
                "add_ports",
                lightingUniverse.getOlaOutputPortId(),
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

    private String getOlaUniverseName(LightingUniverse lightingUniverse) {
        if (lightingUniverse.getName() != null && !lightingUniverse.getName().isBlank()) {
            return lightingUniverse.getName();
        }

        return "Universe " + lightingUniverse.getOlaUniverseId();
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
        updateUniverses(getLightingUniverses());
    }

    @Override
    public synchronized void updateUniverses(List<LightingUniverse> lightingUniverses) {
        if (olaClient == null) {
            // OLA client is not connected
            return;
        }

        logger.debug("Initializing lighting universes on OLA...");

        removeAllOlaPorts(lightingUniverses);
        olaReady = false;

        for (LightingUniverse lightingUniverse : lightingUniverses) {
            if (lightingUniverse.getOlaUniverseId() == null) {
                logger.warn("Skipping lighting universe '{}' because no OLA universe id is configured", lightingUniverse.getName());
                continue;
            }

            if (lightingUniverse.getOlaOutputPortId() == null || lightingUniverse.getOlaOutputPortId().isBlank()) {
                logger.trace("No OLA output port configured for universe '{}'", lightingUniverse.getName());
                continue;
            }

            try {
                createOrUpdateOlaUniverse(lightingUniverse);
                olaReady = true;
            } catch (Exception e) {
                logger.error("Could not create or update universe on OLA", e);
            }
        }

        logger.debug("Lighting universes on OLA initialized");
    }

    private void createOrUpdateOlaUniverse(LightingUniverse lightingUniverse) throws IOException {
        if (olaUniverseExists(lightingUniverse.getOlaUniverseId())) {
            olaClient.setUniverseName(lightingUniverse.getOlaUniverseId(), lightingUniverse.getName());
            addOlaUniversePort(lightingUniverse);
            return;
        }

        createOlaUniverse(lightingUniverse);
        olaClient.setUniverseName(lightingUniverse.getOlaUniverseId(), lightingUniverse.getName());
    }

    private void removeAllOlaPorts(List<LightingUniverse> newLightingUniverses) {
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

        addConfiguredOlaPortIds(portIdsByUniverse, getLightingUniverses());
        addConfiguredOlaPortIds(portIdsByUniverse, newLightingUniverses);

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

    private void addConfiguredOlaPortIds(Map<Integer, Set<String>> portIdsByUniverse, List<LightingUniverse> lightingUniverses) {
        for (LightingUniverse lightingUniverse : lightingUniverses) {
            if (lightingUniverse.getOlaUniverseId() == null
                    || lightingUniverse.getOlaOutputPortId() == null
                    || lightingUniverse.getOlaOutputPortId().isBlank()) {
                continue;
            }

            addOlaPortId(portIdsByUniverse, lightingUniverse.getOlaUniverseId(), lightingUniverse.getOlaOutputPortId());
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
    public void addLightingUniverse(LightingUniverseState lightingUniverse) {
        lightingUniverseList.add(lightingUniverse);
    }

    @Override
    public void removeLightingUniverse(LightingUniverseState lightingUniverse) {
        lightingUniverseList.remove(lightingUniverse);
    }

    @Override
    public void setExternalSync(boolean externalSync) {
        this.externalSync = externalSync;
    }

    @Override
    @PreDestroy
    public void close() {
        if (olaRetryExecutor != null && !olaRetryExecutor.isShutdown()) {
            olaRetryExecutor.shutdownNow();
        }

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
    public synchronized List<OlaPlugin> getOlaPlugins() {
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
    public synchronized List<OlaPort> getOlaOutputPorts() {
        List<OlaPort> olaOutputPorts = new ArrayList<>();

        if (olaClient == null) {
            return olaOutputPorts;
        }

        try {
            olaOutputPorts = fetchLiveOlaOutputPorts();
            addConfiguredOlaOutputPorts(olaOutputPorts);
        } catch (Exception e) {
            logger.error("Could not get OLA output ports", e);
        }

        return olaOutputPorts;
    }

    private void addConfiguredOlaOutputPorts(List<OlaPort> olaOutputPorts) {
        for (LightingUniverse lightingUniverse : getLightingUniverses()) {
            if (lightingUniverse.getOlaOutputPortId() == null
                    || lightingUniverse.getOlaOutputPortId().isBlank()
                    || containsOlaOutputPort(olaOutputPorts, lightingUniverse.getOlaOutputPortId())
                    || lightingUniverse.getOlaUniverseId() == null) {
                continue;
            }

            UniverseInfoReply universeInfoReply = olaClient.getUniverseInfo(lightingUniverse.getOlaUniverseId());
            String description = getOutputPortDescription(universeInfoReply);

            if (description == null || description.isBlank()) {
                continue;
            }

            OlaPort olaPort = new OlaPort();
            olaPort.setId(lightingUniverse.getOlaOutputPortId());
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
    public synchronized void reloadOlaPlugins() {
        if (olaClient == null) {
            return;
        }

        olaClient.reloadPlugins();
        logger.debug("OLA plugins reloaded");
    }

    @Override
    public synchronized void enablePlugins(List<OlaPlugin> olaPluginList) {
        // Disable all plugins, except the one to be enabled
        for (OlaPlugin olaPlugin : getOlaPlugins()) {
            boolean enabled = olaPluginList.stream().anyMatch(plugin -> plugin.getName().equals(olaPlugin.getName()));
            olaClient.setPluginState(olaPlugin.getId(), enabled);
        }
    }

    private LightingUniverseState getLightingUniverseForAction(String universeUuid) {
        if (!hasLightingUniverse(universeUuid)) {
            logger.warn("Skipping lighting action for unknown universe '{}'", universeUuid);
            return null;
        }

        for (LightingUniverseState lightingUniverse : lightingUniverseList) {
            if (Objects.equals(lightingUniverse.getMappingUuid(), universeUuid)) {
                return lightingUniverse;
            }
        }

        LightingUniverseState lightingUniverse = new LightingUniverseState();
        lightingUniverse.setMappingUuid(universeUuid);
        lightingUniverseList.add(lightingUniverse);
        return lightingUniverse;
    }

    private boolean hasLightingUniverse(String universeUuid) {
        if (universeUuid == null || universeUuid.isBlank()) {
            return false;
        }

        for (LightingUniverse lightingUniverse : getLightingUniverses()) {
            if (Objects.equals(lightingUniverse.getUuid(), universeUuid)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void executeAction(ActionLighting actionLighting) {
        for (LightingActionUniverse lightingActionUniverse : actionLighting.getLightingActionUniverseList()) {
            LightingUniverseState lightingUniverse = getLightingUniverseForAction(lightingActionUniverse.getUniverseUuid());
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
