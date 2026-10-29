package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultActionMidiExecutionService implements ActionMidiExecutionService {

    private final static int SONG_SELECT_STATUS = 0xF3;
    private final static int START_STATUS = 0xFA;
    private final static int CONTINUE_STATUS = 0xFB;
    private final static int STOP_STATUS = 0xFC;

    private final SettingsService settingsService;
    private final ActionExecutionService actionExecutionService;

    public DefaultActionMidiExecutionService(SettingsService settingsService, ActionExecutionService actionExecutionService) {
        this.settingsService = settingsService;
        this.actionExecutionService = actionExecutionService;
    }

    /**
     * Does this action mapping match to the current MIDI message and should the
     * action be executed?
     */
    private boolean isActionMappingMatch(ActionTriggerMidi actionTriggerMidi, ShortMessage shortMessage) {
        int status = shortMessage.getStatus();

        // System messages (song select, transport) carry no channel: their low nibble is part of the
        // status byte, so getChannel() would report a channel that was never meant as one.
        if (status < 0xF0) {
            if (actionTriggerMidi.getChannel() != null && actionTriggerMidi.getChannel() != shortMessage.getChannel()) {
                return false;
            }
        }

        // A note on with velocity 0 is a released key (many devices send it instead of a note off)
        if (shortMessage.getCommand() == ShortMessage.NOTE_ON && shortMessage.getData2() == 0) {
            return false;
        }

        if (shortMessage.getCommand() == ShortMessage.NOTE_ON && actionTriggerMidi instanceof ActionTriggerMidiNoteOn actionTriggerMidiNoteOn) {
            return actionTriggerMidiNoteOn.getNote() == null || actionTriggerMidiNoteOn.getNote() == shortMessage.getData1();
        }

        if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE && actionTriggerMidi instanceof ActionTriggerMidiProgramChange actionTriggerMidiProgramChange) {
            return actionTriggerMidiProgramChange.getProgram() == null || actionTriggerMidiProgramChange.getProgram() == shortMessage.getData1();
        }

        if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE && actionTriggerMidi instanceof ActionTriggerMidiControlChange actionTriggerMidiControlChange) {
            return (actionTriggerMidiControlChange.getController() == null || actionTriggerMidiControlChange.getController() == shortMessage.getData1())
                    && (actionTriggerMidiControlChange.getValue() == null || actionTriggerMidiControlChange.getValue() == shortMessage.getData2());
        }

        if (status == SONG_SELECT_STATUS && actionTriggerMidi instanceof ActionTriggerMidiSongSelect actionTriggerMidiSongSelect) {
            return actionTriggerMidiSongSelect.getSong() == null || actionTriggerMidiSongSelect.getSong() == shortMessage.getData1();
        }

        if (actionTriggerMidi instanceof ActionTriggerMidiSystemRealTime actionTriggerMidiSystemRealTime) {
            return getSystemRealTimeType(status) == actionTriggerMidiSystemRealTime.getSystemRealTimeType();
        }

        return false;
    }

    private static ActionTriggerMidiSystemRealTime.SystemRealTimeType getSystemRealTimeType(int status) {
        return switch (status) {
            case START_STATUS -> ActionTriggerMidiSystemRealTime.SystemRealTimeType.START;
            case CONTINUE_STATUS -> ActionTriggerMidiSystemRealTime.SystemRealTimeType.CONTINUE;
            case STOP_STATUS -> ActionTriggerMidiSystemRealTime.SystemRealTimeType.STOP;
            default -> null;
        };
    }

    @Override
    public void processMidiSignal(ShortMessage shortMessage) throws Exception {
        // Map the MIDI event and executeFromTrigger the appropriate actions

        // Search for and executeFromTrigger all required actions
        for (ActionTriggerMidi actionTriggerMidi : settingsService.getSettings().getActionTriggerMidiList()) {
            if (isActionMappingMatch(actionTriggerMidi, shortMessage)) {
                actionExecutionService.executeFromTrigger(actionTriggerMidi);
            }
        }
    }

}
