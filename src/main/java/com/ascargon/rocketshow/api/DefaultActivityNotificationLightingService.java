package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.LightingUniverse;
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
 * Notify all connected websocket clients about a MIDI event.
 */
@Service
public class DefaultActivityNotificationLightingService extends TextWebSocketHandler implements ActivityNotificationLightingService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationLightingService.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private Timer sendActivityTimer;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private synchronized void sendWebsocketMessage(LightingUniverse lightingUniverse) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(lightingUniverse);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                sessions.remove(webSocketSession);
            }
        }
    }

    @Override
    public void notifyClients(LightingUniverse lightingUniverse) {
        if (sendActivityTimer != null) {
            // There is already a timer running -> let it finish and ignore this event for performance reasons
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Send the universe
                    sendWebsocketMessage(lightingUniverse);
                } catch (Exception e) {
                    logger.error("Could not send the lighting activity", e);
                }

                if (sendActivityTimer != null) {
                    sendActivityTimer.cancel();
                }

                sendActivityTimer = null;
            }
        };

        sendActivityTimer = new Timer();
        sendActivityTimer.schedule(timerTask, 50);
    }

    @PreDestroy
    public void close() {
        // Don't close the sessions, because the webapp would not automatically reconnect in this case.
    }

}
