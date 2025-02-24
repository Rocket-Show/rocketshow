package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultMidiControlActionExecutionService implements MidiControlActionExecutionService {

    private final SettingsService settingsService;
    private final ActionExecutionService actionExecutionService;

    public DefaultMidiControlActionExecutionService(SettingsService settingsService, ActionExecutionService actionExecutionService) {
        this.settingsService = settingsService;
        this.actionExecutionService = actionExecutionService;
    }

    /**
     * Does this action mapping match to the current MIDI message and should the
     * action be executed?
     */
    private boolean isActionMappingMatch(MidiActionTrigger midiActionTrigger, ShortMessage shortMessage) {
        if (shortMessage.getCommand() != ShortMessage.NOTE_ON
                && shortMessage.getCommand() != ShortMessage.PROGRAM_CHANGE) {
            return false;
        }

        if (midiActionTrigger.getChannel() != null && midiActionTrigger.getChannel() != shortMessage.getChannel()) {
            return false;
        }

        if (shortMessage.getCommand() == ShortMessage.NOTE_ON
                && midiActionTrigger.getMidiActionTriggerType() == MidiActionTrigger.MidiActionTriggerType.NOTE_ON) {

            return midiActionTrigger.getNote() == null || midiActionTrigger.getProgram() == shortMessage.getData1();
        }

        if (shortMessage.getCommand() == ShortMessage.PROGRAM_CHANGE
                && midiActionTrigger.getMidiActionTriggerType() == MidiActionTrigger.MidiActionTriggerType.PROGRAM_CHANGE) {

            return midiActionTrigger.getProgram() == null || midiActionTrigger.getProgram() == shortMessage.getData1();
        }

        return false;
    }

    @Override
    public void processMidiSignal(ShortMessage shortMessage) throws Exception {
        // Map the MIDI event and executeFromTrigger the appropriate actions

        // Search for and executeFromTrigger all required actions
        for (MidiActionTrigger midiActionTrigger : settingsService.getSettings().getMidiActionTriggerList()) {
            if (isActionMappingMatch(midiActionTrigger, shortMessage)) {
                actionExecutionService.executeFromTrigger(midiActionTrigger);
            }
        }
    }

}
