package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.CapabilitiesService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationLightingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import ola.OlaClient;
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

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DefaultLightingService implements LightingService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultLightingService.class);

    private final SettingsService settingsService;
    private final CapabilitiesService capabilitiesService;
    private final ActivityNotificationLightingService activityNotificationLightingService;

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

    private final List<String> standardDeviceNames = new ArrayList<>();

    private final HttpClient httpClient;

    private boolean externalSync = false;

    public DefaultLightingService(SettingsService settingsService, CapabilitiesService capabilitiesService, ActivityNotificationLightingService activityNotificationLightingService) {
        this.settingsService = settingsService;
        this.capabilitiesService = capabilitiesService;
        this.activityNotificationLightingService = activityNotificationLightingService;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        try {
            olaClient = new OlaClient();
        } catch (Exception e) {
            logger.error("Could not initialize OLA client", e);
            capabilitiesService.getCapabilities().setOla(false);
        }

        if (!capabilitiesService.getCapabilities().isOla()) {
            return;
        }

        reset();

        standardDeviceNames.add("Dummy Device");
        standardDeviceNames.add("ArtNet");
        standardDeviceNames.add("ShowNet");
        standardDeviceNames.add("ESP Net");
        standardDeviceNames.add("SandNet");
        standardDeviceNames.add("Pathport");
        standardDeviceNames.add("E1.31 (DMX over ACN)");
        standardDeviceNames.add("OSC Device");

        initializeUniverse();
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

        send();
    }

    private void sendUniverse() {
        logger.trace("Send the lighting universe");

        // Mix all current universes into one -> highest value per channel wins
        short[] mixedUniverse = new short[512];

        // Copy the list to protect against changes while mixing
        List<LightingUniverse> lightingUniverseListCopy = new CopyOnWriteArrayList<>(lightingUniverseList);

        for (int i = 0; i < 512; i++) {
            int highestValue = 0;

            for (LightingUniverse lightingUniverse : lightingUniverseListCopy) {
                HashMap<Integer, Integer> universe = lightingUniverse.getUniverse();

                if (universe.get(i) != null && universe.get(i) > highestValue) {
                    highestValue = universe.get(i);
                }
            }

            mixedUniverse[i] = (short) highestValue;
        }

        if (olaClient != null) {
            olaClient.streamDmx(1, mixedUniverse);
        }

        if (settingsService.getSettings().getEnableMonitor()) {
            HashMap<Integer, Integer> mixedActivityUniverse = new HashMap<>();
            for (int i = 0; i < 512; i++) {
                mixedActivityUniverse.put(i, (int) mixedUniverse[i]);
            }
            LightingUniverse activityUniverse = new LightingUniverse();
            activityUniverse.setUniverse(mixedActivityUniverse);
            activityNotificationLightingService.notifyClients(activityUniverse);
        }
    }

    // Make sure, this method is synchronized. Otherwise it may happen, that
    // some timers are started in parallel, because different threads send at
    // the same time. This will cause the OLA rpc stream to break and a restart
    // is required.
    @Override
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
                    sendUniverse();
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
    public void sendExternalSync() {
        if (!externalSync) {
            return;
        }

        try {
            // Send the universe immediately
            sendUniverse();
        } catch (Exception e) {
            logger.error("Could not send the lighting universe", e);
        }
    }

    private boolean isStandardDevice(String name) {
        for (String standardDeviceName : standardDeviceNames) {
            if (name.startsWith(standardDeviceName)) {
                return true;
            }
        }

        return false;
    }

    private String getConnectedPort() throws IOException {
        // Query the OLA JSON API for all ports
        HttpGet httpGet = new HttpGet(OLA_URL + "json/get_ports");
        HttpResponse response = httpClient.execute(httpGet);

        // Parse the resulting JSON ports
        ObjectMapper mapper = new ObjectMapper();
        OlaPort[] olaPortList = mapper.readValue(response.getEntity().getContent(), OlaPort[].class);

        // Search for any non-default ports (e.g. a connected lighting USB device)
        for (OlaPort olaPort : olaPortList) {
            if (olaPort.isOutput() && !isStandardDevice(olaPort.getDevice())) {
                return olaPort.getId();
            }
        }

        return null;
    }

    private void createOlaUniverse(String portId)
            throws IOException {

        int universeId = 1;
        String name = "Standard";

        logger.debug("Adding new universe with port '" + portId + "'...");

        HttpClient httpClient;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        HttpPost httpPost = new HttpPost(OLA_URL + "new_universe");

        List<NameValuePair> data = new ArrayList<>(3);

        data.add(new BasicNameValuePair("id", String.valueOf(universeId)));
        data.add(new BasicNameValuePair("name", name));
        data.add(new BasicNameValuePair("add_ports", portId));

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

    private void initializeUniverse() {
        if (olaClient == null) {
            // OLA client is not connected
            return;
        }

        UniverseInfoReply universeInfoReply = olaClient.getUniverseList();

        if (universeInfoReply != null) {
            if (universeInfoReply.getUniverseCount() > 0) {
                // At least one universe is already initialized
                return;
            }
        }

        logger.debug("Initializing lighting universe on OLA...");

        String portId = null;

        try {
            portId = getConnectedPort();
        } catch (Exception e) {
            logger.error("Could not get a output port", e);
        }

        if (portId == null || portId.length() == 0) {
            // No connected lighting device-port found
            logger.trace("No connected lighting output device found");
            return;
        }

        // Create a new universe with the found device
        try {
            // Create the port with the device-id, "O" for output and the port
            // ID
            createOlaUniverse(portId);
        } catch (Exception e) {
            logger.error("Could not create a new universe on OLA", e);
        }

        logger.debug("Lighting universe on OLA initialized");
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

}
