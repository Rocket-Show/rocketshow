package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notify all connected websocket clients about the current device state.
 */
@Service
public class DefaultNotificationService extends TextWebSocketHandler implements NotificationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultNotificationService.class);

    private final StateService stateService;
    private final CompositionService compositionService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    // Every message goes out from this thread, one after the other: a websocket session only sends one message at a
    // time, and the web app shows the state it got last, so a state must never overtake a newer one.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notification-websocket");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultNotificationService(
            StateService stateService,
            CompositionService compositionService
    ) {
        this.stateService = stateService;
        this.compositionService = compositionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void notifyClients(
            PlayerService playerService,
            SetService setService,
            String error
    ) {
        sender.execute(() -> {
            try {
                // The state at the moment it is sent, so it is never older than the one sent before
                State currentState = stateService.getCurrentState(
                        playerService,
                        setService,
                        compositionService
                );

                currentState.setError(error);

                ObjectMapper mapper = new ObjectMapper();
                TextMessage message = new TextMessage(mapper.writeValueAsString(currentState));

                logger.debug("Sending WebSocket message to {} clients", sessions.size());
                for (WebSocketSession webSocketSession : sessions) {
                    send(webSocketSession, message);
                }
            } catch (Exception e) {
                logger.error("Could not notify the clients", e);
            }
        });
    }

    private void send(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            session.sendMessage(message);
        } catch (Exception e) {
            logger.debug("Could not send WebSocket message. Close the session...", e);
            sessions.remove(session);
        }
    }

    @PreDestroy
    public void shutdownSender() {
        sender.shutdownNow();
    }

    @Scheduled(fixedRate = 25000) // every 25 seconds
    public void sendPing() {
        sender.execute(() -> {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    send(session, new PingMessage(ByteBuffer.wrap(new byte[]{1})));
                }
            }
        });
    }

    @Override
    public void notifyClients(PlayerService playerService) throws IOException {
        notifyClients(playerService, null, null);
    }

    @Override
    public void notifyClients(SetService setService) throws IOException {
        notifyClients(null, setService, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService) throws IOException {
        notifyClients(playerService, setService, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService, boolean isUpdateFinished) throws IOException {
        notifyClients(playerService, setService, null);
    }

    @Override
    public void notifyClients(String error) throws IOException {
        notifyClients(null, null, error);
    }

    @Override
    public void notifyClients() throws IOException {
        notifyClients(null, null, null);
    }

}
