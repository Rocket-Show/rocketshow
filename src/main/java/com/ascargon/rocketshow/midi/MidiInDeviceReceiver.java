package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.Settings;
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

    private final ActionMidiExecutionService actionMidiExecutionService;

    private final MidiRouter midiRouter;

    // Gets the input instead of the triggers and the routings, e.g. during the self-test
    private volatile Receiver divertedReceiver;

    MidiInDeviceReceiver(ActionMidiExecutionService actionMidiExecutionService, MidiRouterFactory midiRouterFactory) {
        this.actionMidiExecutionService = actionMidiExecutionService;

        midiRouter = midiRouterFactory.getSettingsMidiRouter(Settings::getDeviceInMidiRoutingList);
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        if (!(midiMessage instanceof ShortMessage shortMessage)) {
            return;
        }

        Receiver divertedReceiver = this.divertedReceiver;
        if (divertedReceiver != null) {
            divertedReceiver.send(shortMessage, timeStamp);
            return;
        }

        // Process MIDI events as actions according to the settings
        try {
            actionMidiExecutionService.processMidiSignal(shortMessage);
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

    void divert(Receiver receiver) {
        divertedReceiver = receiver;
    }

    @Override
    public void close() {
        // Nothing to do
    }

}
