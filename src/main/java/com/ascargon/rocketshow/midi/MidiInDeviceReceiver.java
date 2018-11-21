package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.api.NotificationService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.List;
import java.util.Timer;

/**
 * Handle the MIDI events from the currently connected MIDI input device.
 *
 * @author Moritz A. Vieli
 */
public class MidiInDeviceReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(MidiInDeviceReceiver.class);

    private NotificationService notificationService;
    private MidiControlActionExecutionService midiControlActionExecutionService;

    private boolean midiLearn = false;

    public MidiInDeviceReceiver(NotificationService notificationService, MidiControlActionExecutionService midiControlActionExecutionService) {
        this.notificationService = notificationService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);

        // Notify the frontend, if midi learn is activated
        if (midiLearn) {
            try {
                notificationService.notifyClients(midiSignal);
            } catch (Exception e) {
                logger.error("Could not notify the clients about a MIDI learn event", e);
            }
        }

        // Process MIDI events as actions according to the settings
        try {
            midiControlActionExecutionService.processMidiSignal(midiSignal);
        } catch (Exception e) {
            logger.error("Could not execute action from live MIDI", e);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }

    public void setMidiLearn(boolean midiLearn) {
        this.midiLearn = midiLearn;
    }

}
