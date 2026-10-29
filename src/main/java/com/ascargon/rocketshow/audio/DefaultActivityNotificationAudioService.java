package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notify all connected websocket clients about an audio event.
 */
@Service
public class DefaultActivityNotificationAudioService extends TextWebSocketHandler implements ActivityNotificationAudioService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationAudioService.class);

    private final SettingsService settingsService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // Sends all messages, so a session never gets two at once and nobody waits for a slow web app
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "audio-monitor");
        thread.setDaemon(true);
        return thread;
    });

    // The latest levels to send. Levels arriving while sending replace the pending ones instead of queuing up.
    private final Object pendingLock = new Object();
    private double[] pendingVolumeDbs;

    public DefaultActivityNotificationAudioService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void sendWebsocketMessage(double[] volumeDbs) throws IOException {
        if (settingsService.getSettings().getAudioBusList().isEmpty()) {
            return;
        }

        ActivityAudio activityAudio = new ActivityAudio();

        int currentAudioBusIndex = -1;
        AudioBus currentAudioBus = settingsService.getSettings().getAudioBusList().get(0);
        ActivityAudioBus currentActivityAudioBus = null;
        int currentChannel = Integer.MAX_VALUE;

        // Fill all volumes into the corresponding buses
        for (double volumeDb : volumeDbs) {
            if (currentChannel >= currentAudioBus.getChannels()) {
                currentAudioBusIndex++;

                if (settingsService.getSettings().getAudioBusList().size() > currentAudioBusIndex) {
                    // Create a new activity channel
                    currentAudioBus = settingsService.getSettings().getAudioBusList().get(currentAudioBusIndex);
                    currentActivityAudioBus = new ActivityAudioBus();
                    currentActivityAudioBus.setName(currentAudioBus.getName());
                    activityAudio.getActivityAudioBusList().add(currentActivityAudioBus);
                    currentChannel = 0;
                } else {
                    // More channels present than defined in the audio buses
                    break;
                }
            }

            // Add this channel to the current activity-bus
            ActivityAudioChannel activityAudioChannel = new ActivityAudioChannel();
            activityAudioChannel.setIndex(currentChannel);
            activityAudioChannel.setVolumeDb(volumeDb);
            currentActivityAudioBus.getActivityAudioChannelList().add(activityAudioChannel);

            currentChannel++;
        }

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(activityAudio);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                sessions.remove(webSocketSession);
            }
        }
    }

    private void sendPending() {
        double[] volumeDbs;

        synchronized (pendingLock) {
            volumeDbs = pendingVolumeDbs;
            pendingVolumeDbs = null;
        }

        try {
            sendWebsocketMessage(volumeDbs);
        } catch (Exception e) {
            logger.error("Could not send audio activity message", e);
        }
    }

    @Override
    public void notifyClients(double[] volumeDbs) {
        // Don't block the pipeline sending the levels
        synchronized (pendingLock) {
            boolean sendScheduled = pendingVolumeDbs != null;
            pendingVolumeDbs = volumeDbs;

            if (sendScheduled) {
                return;
            }
        }

        sender.execute(this::sendPending);
    }

    @Scheduled(fixedRate = 25000) // every 25 seconds
    public void sendPing() {
        sender.execute(() -> {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[]{1})));
                    } catch (IOException e) {
                        logger.debug("Ping failed, closing websocket session", e);
                        sessions.remove(session);
                    }
                }
            }
        });
    }

    @PreDestroy
    public void close() {
        sender.shutdownNow();
    }

}
