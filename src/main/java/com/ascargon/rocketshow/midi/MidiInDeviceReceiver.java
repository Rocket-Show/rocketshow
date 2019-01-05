package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/**
 * Handle the MIDI events from the currently connected MIDI input device.
 *
 * @author Moritz A. Vieli
 */
class MidiInDeviceReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(MidiInDeviceReceiver.class);

    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final MidiControlActionExecutionService midiControlActionExecutionService;

    MidiInDeviceReceiver(ActivityNotificationMidiService activityNotificationMidiService, MidiControlActionExecutionService midiControlActionExecutionService) {
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);

        // Notify the frontend, if midi learn is activated
        activityNotificationMidiService.notifyClients(midiSignal, MidiSignal.MidiDirection.IN, MidiSignal.MidiSource.IN_DEVICE, null);

        // Process MIDI events as actions according to the settings
        try {
            midiControlActionExecutionService.processMidiSignal(midiSignal);
        } catch (Exception e) {
            logger.error("Could not execute action from live MIDI", e);
        }

        // TODO Process the MIDI events through the defined routings
    }

    @Override
    public void close() {
        // Nothing to do
    }

}
