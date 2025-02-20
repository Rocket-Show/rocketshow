package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about a MIDI event.
 */
@Service
public class DefaultActivityNotificationMidiService extends TextWebSocketHandler implements ActivityNotificationMidiService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationMidiService.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private final SettingsService settingsService;

    private Timer sendActivityTimer;

    private ActivityMidi activityMidi;

    public DefaultActivityNotificationMidiService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private synchronized void sendWebsocketMessage() throws IOException {
        if (activityMidi == null) {
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(activityMidi);

        for (WebSocketSession webSocketSession : sessions) {
            try {
                webSocketSession.sendMessage(new TextMessage(returnValue));
            } catch (Exception e) {
                sessions.remove(webSocketSession);
            }
        }

        activityMidi = null;
    }

    @Override
    public void notifyClients(MidiSignal midiSignal, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination) {
        if (!settingsService.getSettings().getEnableMonitor()) {
            return;
        }

        // Mix the current event into the pending activity or create a new one
        if (activityMidi == null) {
            // Create a new MIDI activity
            activityMidi = new ActivityMidi();

            activityMidi.setMidiSignal(midiSignal);
            activityMidi.setMidiDirection(midiDirection);
            if (midiSource != null) {
                activityMidi.getMidiSources().add(midiSource);
            }
            if (midiDestination != null) {
                activityMidi.getMidiDestinations().add(midiDestination);
            }
        } else {
            // Mix the current MIDI event into the pending activity
            // TODO mix the signal

            if (!activityMidi.getMidiDirection().equals(midiDirection)) {
                activityMidi.setMidiDirection(MidiDirection.IN_OUT);
            }

            if (midiSource != null && !activityMidi.getMidiSources().contains(midiSource)) {
                activityMidi.getMidiSources().add(midiSource);
            }

            if (midiDestination != null && !activityMidi.getMidiDestinations().contains(midiDestination)) {
                activityMidi.getMidiDestinations().add(midiDestination);
            }
        }

        if (sendActivityTimer != null) {
            // There is already a timer running -> let it finish and ignore this event for performance reasons
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Send the universe
                    sendWebsocketMessage();
                } catch (Exception e) {
                    logger.error("Could not send the MIDI activity", e);
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

}
