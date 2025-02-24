package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
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

    private final MidiControlActionExecutionService midiControlActionExecutionService;

    private final MidiRouter midiRouter;

    MidiInDeviceReceiver(MidiControlActionExecutionService midiControlActionExecutionService, SettingsService settingsService, MidiRouterFactory midiRouterFactory) {
        this.midiControlActionExecutionService = midiControlActionExecutionService;

        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getDeviceInMidiRoutingList());
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        if (!(midiMessage instanceof ShortMessage shortMessage)) {
            return;
        }

        // Process MIDI events as actions according to the settings
        try {
            midiControlActionExecutionService.processMidiSignal(shortMessage);
        } catch (Exception e) {
            logger.error("Could not executeFromTrigger action from MIDI device", e);
        }

        // Process the MIDI events through the defined routings
        try {
            midiRouter.sendSignal(shortMessage, MidiSource.IN_DEVICE);
        } catch (InvalidMidiDataException e) {
            logger.error("Could not route event from MIDI device", e);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }

}
