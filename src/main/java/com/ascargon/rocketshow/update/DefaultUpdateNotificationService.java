package com.ascargon.rocketshow.update;

import com.ascargon.rocketshow.midi.DefaultActivityNotificationMidiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DefaultUpdateNotificationService extends TextWebSocketHandler implements UpdateNotificationService {

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

    @Override
    public void notifyClients(UpdateState updateState) {
        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(updateState);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                logger.error("Could not send WebSocket update message. Close the session...", e);
                sessions.remove(webSocketSession);
            }
        }
    }

}
