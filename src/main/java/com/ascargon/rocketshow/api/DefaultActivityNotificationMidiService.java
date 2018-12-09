package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;
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
 * Notify all connected websocket clients about a MIDI event.
 */
@Service
public class DefaultActivityNotificationMidiService extends TextWebSocketHandler implements ActivityNotificationMidiService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationMidiService.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private synchronized void sendWebsocketMessage(MidiSignal midiSignal, ActivityMidi.MidiSource midiSource) throws IOException {
        ActivityMidi activityMidi = new ActivityMidi();

        activityMidi.setMidiSignal(midiSignal);
        activityMidi.setMidiSource(midiSource);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(activityMidi);

        for (WebSocketSession webSocketSession : sessions) webSocketSession.sendMessage(new TextMessage(returnValue));
    }

    @Override
    public void notifyClients(MidiSignal midiSignal, ActivityMidi.MidiSource midiSource) {
        // Wrap in a thread, to not block the main thread and make synchronized calls
        // to websocket (two writes to the same session from different threads is not allowed)
        Thread thread = new Thread(() -> {
            try {
                sendWebsocketMessage(midiSignal, midiSource);
            } catch (IOException e) {
                logger.error("Could not send MIDI activity message", e);
            }
        });
        thread.start();
    }

    @PreDestroy
    public void close() {
        // Don't close the sessions, because the webapp would not automatically reconnect in this case.
    }

}
