package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.Updater.UpdateState;
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
public class WebSocketNotificationService extends TextWebSocketHandler implements NotificationService {

    private StateService stateService;

    private List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public WebSocketNotificationService(StateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void notifyClients(MidiSignal midiSignal, UpdateState updateState) throws IOException {
        State currentState = stateService.getCurrentState();
        currentState.setMidiSignal(midiSignal);
        currentState.setUpdateState(updateState);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(currentState);

        // TODO Async
        for (WebSocketSession webSocketSession : sessions) webSocketSession.sendMessage(new TextMessage(returnValue));
    }

    // Notify the clients about the current state and include a midi signal, if
    // midi learn is activated
    public void notifyClients(MidiSignal midiSignal) throws IOException {
        notifyClients(midiSignal, null);
    }

    // Notify the clients about the current state and include update
    // information, if an update is running
    public void notifyClients(UpdateState updateState) throws IOException {
        notifyClients(null, updateState);
    }

    public void notifyClients() throws IOException {
        notifyClients(null, null);
    }

}
