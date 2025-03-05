package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultActionMidiExecutionService implements ActionMidiExecutionService {

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
        if (actionTriggerMidi.getChannel() != null && actionTriggerMidi.getChannel() != shortMessage.getChannel()) {
            return false;
        }

        if (shortMessage.getCommand() == ShortMessage.NOTE_ON && actionTriggerMidi instanceof ActionTriggerMidiNoteOn actionTriggerMidiNoteOn) {
            return actionTriggerMidiNoteOn.getNote() == null || actionTriggerMidiNoteOn.getNote() == shortMessage.getData1();
        }

        if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE && actionTriggerMidi instanceof ActionTriggerMidiProgramChange actionTriggerMidiProgramChange) {
            return actionTriggerMidiProgramChange.getProgram() == null || actionTriggerMidiProgramChange.getProgram() == shortMessage.getData1();
        }

        return false;
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
