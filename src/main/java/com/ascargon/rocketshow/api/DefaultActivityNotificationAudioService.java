package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about an audio event.
 */
@Service
public class DefaultActivityNotificationAudioService extends TextWebSocketHandler implements ActivityNotificationAudioService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationAudioService.class);

    private final SettingsService settingsService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

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

    private synchronized void sendWebsocketMessage(double[] volumeDbs) throws IOException {
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

    @Override
    public void notifyClients(double[] volumeDbs) {
        // Wrap in a thread, to not block the main thread and make synchronized calls
        // to websocket (two writes to the same session from different threads is not allowed)
        Thread thread = new Thread(() -> {
            try {
                sendWebsocketMessage(volumeDbs);
            } catch (IOException e) {
                logger.error("Could not send audio activity message", e);
            }
        });
        thread.start();
    }

    @PreDestroy
    public void close() {
        // Don't close the sessions, because the webapp would not automatically reconnect in this case.
    }

}
