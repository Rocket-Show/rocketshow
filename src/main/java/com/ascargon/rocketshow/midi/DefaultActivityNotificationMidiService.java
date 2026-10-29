package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
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

import javax.sound.midi.ShortMessage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notify all connected websocket clients about a MIDI event.
 */
@Service
public class DefaultActivityNotificationMidiService extends TextWebSocketHandler implements ActivityNotificationMidiService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActivityNotificationMidiService.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private final SettingsService settingsService;

    // Sends all messages, so a session never gets two at once and the MIDI routing never waits for a slow web app
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "midi-monitor");
        thread.setDaemon(true);
        return thread;
    });

    // The MIDI events of the last 50 milliseconds, mixed into one activity for performance reasons. Events come from
    // several threads at once (e.g. a MIDI device and a MIDI file).
    private final Object pendingLock = new Object();
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

    private void sendPending() {
        ActivityMidi pendingActivityMidi;

        synchronized (pendingLock) {
            pendingActivityMidi = activityMidi;
            activityMidi = null;
        }

        try {
            String returnValue = new ObjectMapper().writeValueAsString(pendingActivityMidi);

            for (WebSocketSession webSocketSession : sessions) {
                try {
                    webSocketSession.sendMessage(new TextMessage(returnValue));
                } catch (Exception e) {
                    sessions.remove(webSocketSession);
                }
            }
        } catch (Exception e) {
            logger.error("Could not send the MIDI activity", e);
        }
    }

    @Override
    public void notifyClients(ShortMessage shortMessage, MidiDirection midiDirection, MidiSource midiSource, MidiDestination midiDestination) {
        if (!settingsService.getSettings().getEnableMonitor()) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal(shortMessage);

        synchronized (pendingLock) {
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

                sender.schedule(this::sendPending, 50, TimeUnit.MILLISECONDS);
                return;
            }

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
