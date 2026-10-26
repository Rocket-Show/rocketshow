package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @JsonIgnore
    private final HttpClient httpClient;

    // The name of the remote device
    private String name;

    // The host address (IP or hostname) of the remote device
    private String host;

    // Synchronize composition plays/stops with the local device
    private boolean synchronize;

    private String apiKey;

    // TODO Add these timeouts to the settings
    private final static int CONNECT_TIMEOUT_MILLIS = 10000;

    // A device, which accepts the request but never answers (e.g. it hangs or lost its power in the middle of
    // a request), must not keep the request and its connection forever. Otherwise, all connections to the
    // device are used up and every play waits for the remote devices to load.
    private final static int READ_TIMEOUT_MILLIS = 30000;

    // A unit which doesn't answer holds up the requests after it, each up to the timeouts. Requests beyond this many
    // waiting are dropped instead of filling the memory, e.g. with MIDI clock.
    private final static int MAX_WAITING_REQUESTS = 1000;

    // How long a caller waits for its request at most, including the requests before it
    private final static int MAX_WAIT_MILLIS = 60000;

    // Sends the requests to a unit one after the other, in the order they were made, so e.g. a note off never arrives
    // before its note on. By host, because saving the settings creates new remote devices.
    private final static Map<String, Sender> senders = new ConcurrentHashMap<>();

    private static class Sender {

        private final AtomicBoolean dropping = new AtomicBoolean();

        // A unit which doesn't answer fails every request, e.g. each MIDI clock. Its failure is logged once, until it
        // answers again.
        private final AtomicBoolean failing = new AtomicBoolean();

        private final ThreadPoolExecutor executor;

        private Sender(String host) {
            executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(MAX_WAITING_REQUESTS), runnable -> {
                Thread thread = new Thread(runnable, "remote-device-" + host);
                thread.setDaemon(true);
                return thread;
            }, (runnable, executor) -> {
                if (dropping.compareAndSet(false, true)) {
                    logger.warn("Unit '{}' doesn't keep up with the requests, dropping requests until it does", host);
                }

                // Don't let a caller wait for a request which is never sent
                ((Future<?>) runnable).cancel(false);
            });
            executor.allowCoreThreadTimeOut(true);
        }

    }

    private RemoteDevice() {
        this(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    // Package visible for the tests
    RemoteDevice(int connectTimeoutMillis, int readTimeoutMillis) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMillis)
                .setSocketTimeout(readTimeoutMillis)
                .setConnectionRequestTimeout(readTimeoutMillis)
                .build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    }

    private void logFailure(Sender sender, String message, Object... arguments) {
        if (sender.failing.compareAndSet(false, true)) {
            logger.error(message + " (until it answers again, further failures are only logged when debugging)", arguments);
        } else {
            logger.debug(message, arguments);
        }
    }

    private void executeRequest(Sender sender, String url, Object payload) {
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader(ApiKeyAuthFilter.API_KEY_HEADER_NAME, apiKey);

            HttpResponse response;

            if (payload != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(payload);
                httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            }

            response = httpClient.execute(httpPost);

            // The body can only be read once
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());

            logger.debug("Response from remote device POST: {}", body);

            if (response.getStatusLine().getStatusCode() != 200) {
                logFailure(sender, "Could not executeFromTrigger action on remote device with URL '{}'. Reason: '{}'. Body: {}", url, response.getStatusLine().getReasonPhrase(), body);
            } else if (sender.failing.compareAndSet(true, false)) {
                logger.info("Unit '{}' answers again", name);
            }
        } catch (Exception e) {
            logFailure(sender, "Could not executeFromTrigger action on remote device '{}' with url '{}'", name, url, e);
        }
    }

    private void doPost(String apiUrl, Object payload, boolean synchronous) {
        // Build the url for the post request
        String url = "http://" + host + "/api/" + apiUrl;

        Sender sender = senders.computeIfAbsent(String.valueOf(host), Sender::new);

        Future<?> request = sender.executor.submit(() -> {
            executeRequest(sender, url, payload);

            if (sender.executor.getQueue().isEmpty() && sender.dropping.compareAndSet(true, false)) {
                logger.info("Unit '{}' caught up with the requests", host);
            }
        });

        if (!synchronous) {
            return;
        }

        try {
            request.get(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (CancellationException | ExecutionException e) {
            // Dropped or failed, already logged
        } catch (TimeoutException e) {
            logger.error("Request to unit '{}' with url '{}' not sent within {} ms, continuing without waiting", name, url, MAX_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        doPost("transport/play-as-sample?name=" + URLEncoder.encode(compositionName, StandardCharsets.UTF_8));
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

    public void setCompositionName(String compositionName) {
        doPost("transport/set-composition-name?name=" + URLEncoder.encode(compositionName, StandardCharsets.UTF_8), null, true);
    }

    public void sendMidiSignal(MidiSignal midiSignal) {
        doPost("midi/send-message", midiSignal);
    }

}
