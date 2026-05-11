package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.update.UpdateState;
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

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notify all connected websocket clients about the current device state.
 */
@Service
public class DefaultNotificationService extends TextWebSocketHandler implements NotificationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultNotificationService.class);

    private final StateService stateService;
    private final CompositionService compositionService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<WebSocketSession, Queue<String>> queuedMessages = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> scheduledRetries = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "notification-websocket-retry");
        thread.setDaemon(true);
        return thread;
    });

    private static final long WEBSOCKET_RETRY_DELAY_MS = 100;
    private static final String TEXT_PARTIAL_WRITING_STATE = "TEXT_PARTIAL_WRITING";

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
        queuedMessages.remove(session);
        scheduledRetries.remove(session);
    }

    private void notifyClients(
            PlayerService playerService,
            SetService setService,
            String error
    ) {

        State currentState = stateService.getCurrentState(
                playerService,
                setService,
                compositionService
        );

        currentState.setError(error);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(currentState);

        logger.debug("Sending WebSocket message to {} clients", sessions.size());
        for (WebSocketSession webSocketSession : sessions) {
            sendOrQueueMessage(webSocketSession, returnValue);
        }
    }

    private void sendOrQueueMessage(WebSocketSession session, String payload) {
        Queue<String> pendingMessages = queuedMessages.get(session);
        if (pendingMessages != null && !pendingMessages.isEmpty()) {
            queueMessage(session, payload);
            return;
        }

        try {
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            if (isTextPartialWriting(e)) {
                logger.debug("WebSocket session is already writing a text message. Queue the message for retry...", e);
                queueMessage(session, payload);
                return;
            }

            logger.error("Could not send WebSocket message. Close the session...", e);
            sessions.remove(session);
        }
    }

    private void queueMessage(WebSocketSession session, String payload) {
        queuedMessages.computeIfAbsent(session, ignored -> new ConcurrentLinkedQueue<>()).add(payload);
        scheduleQueuedMessages(session);
    }

    private void scheduleQueuedMessages(WebSocketSession session) {
        if (!scheduledRetries.add(session)) {
            return;
        }

        retryExecutor.schedule(() -> sendQueuedMessages(session), WEBSOCKET_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendQueuedMessages(WebSocketSession session) {
        scheduledRetries.remove(session);

        Queue<String> pendingMessages = queuedMessages.get(session);
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        if (!session.isOpen()) {
            sessions.remove(session);
            queuedMessages.remove(session);
            return;
        }

        while (!pendingMessages.isEmpty()) {
            String payload = pendingMessages.peek();
            try {
                session.sendMessage(new TextMessage(payload));
                pendingMessages.poll();
            } catch (Exception e) {
                if (isTextPartialWriting(e)) {
                    logger.debug("WebSocket session is still writing a text message. Retry queued message shortly...", e);
                    scheduleQueuedMessages(session);
                    return;
                }

                logger.error("Could not send queued WebSocket message. Close the session...", e);
                sessions.remove(session);
                queuedMessages.remove(session);
                return;
            }
        }
    }

    private boolean isTextPartialWriting(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof IllegalStateException && message != null && message.contains(TEXT_PARTIAL_WRITING_STATE)) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    @PreDestroy
    public void shutdownRetryExecutor() {
        retryExecutor.shutdownNow();
    }

    @Scheduled(fixedRate = 25000) // every 25 seconds
    public void sendPing() {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new PingMessage(ByteBuffer.wrap(new byte[]{1})));
                } catch (IOException e) {
                    if (isTextPartialWriting(e)) {
                        // Ignore the ping, if we were already writing to the websocket
                        return;
                    }

                    logger.debug("Ping failed, closing websocket session", e);
                    sessions.remove(session);
                }
            }
        }
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
