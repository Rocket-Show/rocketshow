package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.util.UpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about the current device state.
 */
@Service
public class DefaultNotificationService extends TextWebSocketHandler implements NotificationService {

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

    private synchronized void notifyClients(PlayerService playerService, SetService setService, UpdateService.UpdateState updateState, Boolean isUpdateFinished, String error) throws IOException {
        State currentState = stateService.getCurrentState(playerService, setService, compositionService);
        currentState.setUpdateState(updateState);
        currentState.setUpdateFinished(isUpdateFinished);
        currentState.setError(error);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(currentState);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                sessions.remove(webSocketSession);
            }
        }
    }

    // Notify the clients about the current state and include update
    // information, if an update is running
    @Override
    public void notifyClients(UpdateService.UpdateState updateState) throws IOException {
        notifyClients(null, null, updateState, null, null);
    }

    @Override
    public void notifyClients(PlayerService playerService) throws IOException {
        notifyClients(playerService, null, null, null, null);
    }

    @Override
    public void notifyClients(SetService setService) throws IOException {
        notifyClients(null, setService, null, null, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService) throws IOException {
        notifyClients(playerService, setService, null, null, null);
    }

    @Override
    public void notifyClients(PlayerService playerService, SetService setService, boolean isUpdateFinished) throws IOException {
        notifyClients(playerService, setService, null, isUpdateFinished, null);
    }

    @Override
    public void notifyClients(String error) throws IOException {
        notifyClients(null, null, null, null, error);
    }

    @Override
    public void notifyClients() throws IOException {
        notifyClients(null, null, null, null, null);
    }

}
