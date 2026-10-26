package com.ascargon.rocketshow.lighting;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notify all connected websocket clients about the DMX values sent.
 */
@Service
public class DefaultActivityNotificationLightingService extends TextWebSocketHandler implements ActivityNotificationLightingService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationLightingService.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // Sends all messages, so a session never gets two at once and nobody waits for a slow web app
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lighting-monitor");
        thread.setDaemon(true);
        return thread;
    });

    // The latest values, sent at most every 50 milliseconds for performance reasons. The latest, because the last
    // values of a change (e.g. a blackout) must not get lost.
    private final Object pendingLock = new Object();
    private LightingUniverseState pendingLightingUniverse;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void sendPending() {
        LightingUniverseState lightingUniverse;

        synchronized (pendingLock) {
            lightingUniverse = pendingLightingUniverse;
            pendingLightingUniverse = null;
        }

        try {
            String returnValue = new ObjectMapper().writeValueAsString(lightingUniverse);

            for (WebSocketSession webSocketSession : sessions) {
                try {
                    webSocketSession.sendMessage(new TextMessage(returnValue));
                } catch (Exception e) {
                    sessions.remove(webSocketSession);
                }
            }
        } catch (Exception e) {
            logger.error("Could not send the lighting activity", e);
        }
    }

    @Override
    public void notifyClients(LightingUniverseState lightingUniverse) {
        synchronized (pendingLock) {
            boolean sendScheduled = pendingLightingUniverse != null;
            pendingLightingUniverse = lightingUniverse;

            if (sendScheduled) {
                return;
            }
        }

        sender.schedule(this::sendPending, 50, TimeUnit.MILLISECONDS);
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
