package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Defines a remote RocketShow device to be triggered by the local one one.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
public class RemoteDevice {

    private final static Logger logger = LoggerFactory.getLogger(RemoteDevice.class);

    private final HttpClient httpClient;

    // The name of the remote device
    private String name;

    // The host address (IP or hostname) of the remote device
    private String host;

    // Synchronize composition plays/stops with the local device
    private boolean synchronize;

    private RemoteDevice() {
        // TODO Add this timeout to the settings
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(10000 /* 10 seconds */).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    }

    private void executeRequest(String url, Object payload) {
        try {
            HttpPost httpPost = new HttpPost(url);
            HttpResponse response;

            if (payload != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(payload);
                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            }

            response = httpClient.execute(httpPost);

            logger.debug("Response from remote device POST: " + EntityUtils.toString(response.getEntity()));

            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error("Could not executeFromTrigger action on remote device with URL '" + url + "'. Reason: '" + response.getStatusLine().getReasonPhrase() + "'. Body: " + EntityUtils.toString(response.getEntity()));
            }
        } catch (Exception e) {
            logger.error("Could not executeFromTrigger action on remote device '" + name + "' with url '" + url + "'", e);
        }
    }

    private void doPost(String apiUrl, Object payload, boolean synchronous) {
        // Build the url for the post request
        String url = "http://" + host + "/api/" + apiUrl;

        if (synchronous) {
            executeRequest(url, payload);
        } else {
            new Thread(() -> executeRequest(url, payload)).start();
        }
    }

    public void doPost(String apiUrl, Object payload) {
        doPost(apiUrl, payload, false);
    }

    public void doPost(String apiUrl) {
        doPost(apiUrl, null, false);
    }

    public void reboot() {
        doPost("system/reboot");
    }

    public void shutdown() {
        doPost("system/shutdown");
    }

    public void load(boolean synchronous, String name) {
        doPost("transport/load?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null, synchronous);
    }

    public void load() {
        doPost("transport/load", null, false);
    }

    public void play() {
        doPost("transport/play");
    }

    public void playAsSample(String compositionName) {
        doPost("transport/play-as-sample?name=" + compositionName);
    }

    public void pause() {
        doPost("transport/pause");
    }

    public void stop(boolean playDefaultComposition) {
        doPost("transport/stop?playDefaultComposition=" + playDefaultComposition);
    }

    public void togglePlay() {
        doPost("transport/toggle-play");
    }

    public void setNextComposition() {
        doPost("transport/next-composition");
    }

    public void setPreviousComposition() {
        doPost("transport/previous-composition");
    }

    public void setCompositionName(String compositionName) {
        doPost("transport/set-composition-name?name=" + compositionName, null, true);
    }

    public void sendMidiSignal(MidiSignal midiSignal) {
        doPost("midi/send-message", midiSignal);
    }

}
