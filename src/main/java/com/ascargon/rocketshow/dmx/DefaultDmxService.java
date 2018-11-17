package com.ascargon.rocketshow.dmx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

import ola.OlaClient;
import ola.proto.Ola.UniverseInfoReply;
import org.springframework.stereotype.Service;

@Service
public class DefaultDmxService implements DmxService {

    private final static Logger logger = Logger.getLogger(DefaultDmxService.class);

    private final String OLA_URL = "http://localhost:9090/";

    private Manager manager;

    // Cache the channel values and send them each time
    private List<DmxUniverse> dmxUniverseList = new CopyOnWriteArrayList<>();

    private OlaClient olaClient;

    // Delay sending of the universe because of 2 reasons:
    // - Performance: Sending the whole universe each midi event is not fast
    // enough
    // - Glitches: If we send each event separately, you can see the transitions
    // even if they're not meant to be (e.g. activate two channels at the same
    // time, but sent separately)
    private Timer sendUniverseTimer;

    private List<String> standardDeviceNames = new ArrayList<>();

    private HttpClient httpClient;

    public DefaultDmxService(Manager manager) {
        this.manager = manager;

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        try {
            olaClient = new OlaClient();
        } catch (Exception e) {
            logger.error("Could not initialize OLA DMX client", e);
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
        // Initialize the universe
        for (DmxUniverse dmxUniverse : dmxUniverseList) {
            HashMap<Integer, Integer> universe = dmxUniverse.getUniverse();

            for (int i = 0; i < 512; i++) {
                universe.put(i, 0);
            }
        }

        send();
    }

    private void sendUniverse() {
        if (olaClient == null) {
            return;
        }

        logger.trace("Send the DMX universe");

        // Mix all current universes into one -> highest value per channel wins
        short[] mixedUniverse = new short[512];

        // Copy the list to protect against changes while mixing
        List<DmxUniverse> dmxUniverseListCopy = new CopyOnWriteArrayList<>(dmxUniverseList);

        for (int i = 0; i < 512; i++) {
            int highestValue = 0;

            for (DmxUniverse dmxUniverse : dmxUniverseListCopy) {
                HashMap<Integer, Integer> universe = dmxUniverse.getUniverse();

                if (universe.get(i) != null && universe.get(i) > highestValue) {
                    highestValue = universe.get(i);
                }
            }

            mixedUniverse[i] = (short) highestValue;
        }

        olaClient.streamDmx(1, mixedUniverse);
    }

    // Make sure, this method is synchronized. Otherwise it may happen, that
    // some timers are started in parallel, because different threads send at
    // the same time. This will cause the OLA rpc stream to break and a restart
    // is required.
    public synchronized void send() {
        logger.trace("Sending a DMX value");

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
                    logger.error("Could not send the DMX universe", e);
                }

                if (sendUniverseTimer != null) {
                    sendUniverseTimer.cancel();
                }

                sendUniverseTimer = null;
            }
        };

        sendUniverseTimer = new Timer();
        sendUniverseTimer.schedule(timerTask, manager.getSettings().getDmxSendDelayMillis());
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

        // Search for any non-default ports (e.g. a connected DMX USB device)
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

        logger.debug("Initializing DMX universe on OLA...");

        String portId = null;

        try {
            portId = getConnectedPort();
        } catch (Exception e) {
            logger.error("Could not get a output port", e);
        }

        if (portId == null || portId.length() == 0) {
            // No connected DMX device-port found
            logger.trace("No connected DMX output device found");
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

        logger.debug("DMX universe on OLA initialized");
    }

    void addDmxUniverse(DmxUniverse dmxUniverse) {
        dmxUniverseList.add(dmxUniverse);
    }

    void removeDmxUniverse(DmxUniverse dmxUniverse) {
        dmxUniverseList.remove(dmxUniverse);
    }

    public void close() {
        if (sendUniverseTimer != null) {
            sendUniverseTimer.cancel();
            sendUniverseTimer = null;
        }
    }

}
