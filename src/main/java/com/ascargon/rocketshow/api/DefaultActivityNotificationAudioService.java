package com.ascargon.rocketshow.api;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about an audio event.
 */
@Service
public class DefaultActivityNotificationAudioService extends TextWebSocketHandler implements ActivityNotificationAudioService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationAudioService.class);

    // Collect all activity before sending it
    private Timer sendActivityTimer;

    private ActivityAudio activityAudio;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void sendWebsocketMessage() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(activityAudio);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                sessions.remove(webSocketSession);
            }
        };

        activityAudio = null;
    }

    @Override
    public synchronized void notifyClients(AudioBus audioBus, int channelIndex, double volumeDb) {
        if(activityAudio == null) {
            activityAudio = new ActivityAudio();
        }

        // Merge this activity into the existing one
        ActivityAudioBus mergeActivityAudioBus = null;

        for(ActivityAudioBus activityAudioBus : activityAudio.getActivityAudioBusList()) {
            if(activityAudioBus.getName().equals(audioBus.getName())) {
                mergeActivityAudioBus = activityAudioBus;
                break;
            }
        }

        if(mergeActivityAudioBus == null) {
            mergeActivityAudioBus = new ActivityAudioBus();
            mergeActivityAudioBus.setName(audioBus.getName());
            activityAudio.getActivityAudioBusList().add(mergeActivityAudioBus);
        }

        ActivityAudioChannel mergeActivityAudioChannel = null;

        for(ActivityAudioChannel activityAudioChannel : mergeActivityAudioBus.getActivityAudioChannelList()) {
            if(activityAudioChannel.getIndex() == channelIndex) {
                mergeActivityAudioChannel = activityAudioChannel;
                break;
            }
        }

        if(mergeActivityAudioChannel == null) {
            mergeActivityAudioChannel = new ActivityAudioChannel();
            mergeActivityAudioChannel.setIndex(channelIndex);
            mergeActivityAudioBus.getActivityAudioChannelList().add(mergeActivityAudioChannel);
        }

        mergeActivityAudioChannel.setVolumeDb(volumeDb);

        // Schedule the specified count of executions in the specified delay
        if (sendActivityTimer != null) {
            // There is already a timer running -> let it finish
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Send the universe
                    sendWebsocketMessage();
                } catch (Exception e) {
                    logger.error("Could not send the DMX universe", e);
                }

                if (sendActivityTimer != null) {
                    sendActivityTimer.cancel();
                }

                sendActivityTimer = null;
            }
        };

        sendActivityTimer = new Timer();
        sendActivityTimer.schedule(timerTask,50);
    }

    @PreDestroy
    public void close() {
        // Don't close the sessions, because the webapp would not automatically reconnect in this case.
    }

}
