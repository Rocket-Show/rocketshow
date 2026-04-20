package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.update.UpdateService;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about the current device state.
 */
@Service
public class DefaultNotificationService extends TextWebSocketHandler implements NotificationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultNotificationService.class);

    private final StateService stateService;
    private final CompositionService compositionService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public DefaultNotificationService(StateService stateService, CompositionService compositionService) {
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
            UpdateState updateState,
            String error
    ) throws IOException {

        State currentState = stateService.getCurrentState(playerService, setService, compositionService);
        currentState.setUpdateState(updateState);
        currentState.setError(error);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(currentState);

        logger.debug("Sending WebSocket message to {} clients", sessions.size());
        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                logger.error("Could not send WebSocket message. Close the session...", e);
                sessions.remove(webSocketSession);
            }
        }
    }

    @Scheduled(fixedRate = 25000) // every 25 seconds
    public void sendPing() {
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
    }

    // Notify the clients about the current state and include update
    // information, if an update is running
    @Override
    public void notifyClients(UpdateState updateState) throws IOException {
        notifyClients(null, null, updateState, null);
    }

    @Override
    public void notifyClients(PlayerService playerService) throws IOException {
        notifyClients(playerService, null, null, null);
    }

    @Override
    public void notifyClients(SetService setService) throws IOException {
        notifyClients(null, setService, null, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService) throws IOException {
        notifyClients(playerService, setService, null, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService, boolean isUpdateFinished) throws IOException {
        notifyClients(playerService, setService, null, null);
    }

    @Override
    public void notifyClients(String error) throws IOException {
        notifyClients(null, null, null, error);
    }

    @Override
    public void notifyClients() throws IOException {
        notifyClients(null, null, null, null);
    }

}
