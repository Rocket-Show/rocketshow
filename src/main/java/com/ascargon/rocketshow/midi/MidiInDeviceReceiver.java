package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.InvalidMidiDataException;
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

    private MidiRouter midiRouter;

    MidiInDeviceReceiver(ActivityNotificationMidiService activityNotificationMidiService, MidiControlActionExecutionService midiControlActionExecutionService, SettingsService settingsService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService) {
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.midiControlActionExecutionService = midiControlActionExecutionService;

        midiRouter = new MidiRouter(settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService, activityNotificationMidiService, settingsService.getSettings().getDeviceInMidiRoutingList());
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
            logger.error("Could not execute action from MIDI device", e);
        }

        // Process the MIDI events through the defined routings
        try {
            midiRouter.sendSignal(midiSignal);
        } catch (InvalidMidiDataException e) {
            logger.error("Could not route event from MIDI device", e);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }

}
