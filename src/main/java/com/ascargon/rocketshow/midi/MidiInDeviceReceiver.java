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

    private final MidiTimecodeSlaveService midiTimecodeSlaveService;

    private final MidiCompositionSelectionService midiCompositionSelectionService;

    private final MidiShowControlService midiShowControlService;

    private final MidiRouter midiRouter;

    // Gets the input instead of the triggers and the routings, e.g. during the self-test
    private volatile Receiver divertedReceiver;

    MidiInDeviceReceiver(ActionMidiExecutionService actionMidiExecutionService, MidiTimecodeSlaveService midiTimecodeSlaveService,
                         MidiCompositionSelectionService midiCompositionSelectionService, MidiShowControlService midiShowControlService,
                         MidiRouterFactory midiRouterFactory) {
        this.actionMidiExecutionService = actionMidiExecutionService;
        this.midiTimecodeSlaveService = midiTimecodeSlaveService;
        this.midiCompositionSelectionService = midiCompositionSelectionService;
        this.midiShowControlService = midiShowControlService;

        midiRouter = midiRouterFactory.getSettingsMidiRouter(Settings::getDeviceInMidiRoutingList);
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        // Diverted first: while the input belongs to something else (the self-test), nothing may act
        // on it - not the triggers, not the routings and not a master controlling this device.
        Receiver divertedReceiver = this.divertedReceiver;
        if (divertedReceiver != null) {
            divertedReceiver.send(midiMessage, timeStamp);
            return;
        }

        // Let everything that can be controlled from a master see the message, and for all message
        // types: a timecode locate, show control and machine control all arrive as SysEx.
        try {
            midiTimecodeSlaveService.processMidiMessage(midiMessage);
        } catch (Exception e) {
            logger.error("Could not process the MIDI timecode from the MIDI device", e);
        }

        try {
            midiCompositionSelectionService.processMidiMessage(midiMessage);
        } catch (Exception e) {
            logger.error("Could not select a composition from the MIDI device", e);
        }

        try {
            midiShowControlService.processMidiMessage(midiMessage);
        } catch (Exception e) {
            logger.error("Could not process the show control message from the MIDI device", e);
        }

        if (!(midiMessage instanceof ShortMessage shortMessage)) {
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
