package com.ascargon.rocketshow.midi;

import javax.sound.midi.ShortMessage;
import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.ControlActionExecutionService;
import org.springframework.stereotype.Service;

@Service
public class DefaultMidiControlActionExecutionService implements MidiControlActionExecutionService {

    private SettingsService settingsService;
    private ControlActionExecutionService controlActionExecutionService;

    public DefaultMidiControlActionExecutionService(SettingsService settingsService, ControlActionExecutionService controlActionExecutionService) {
        this.settingsService = settingsService;
        this.controlActionExecutionService = controlActionExecutionService;
    }

    /**
     * Does this action mapping match to the current MIDI message and should the
     * action be executed?
     */
    private boolean isActionMappingMatch(MidiControl midiControl, int channel, int note) {
        return (midiControl.getChannelFrom() == null || midiControl.getChannelFrom() == channel)
                && (midiControl.getNoteFrom() == null || midiControl.getNoteFrom() == note);
    }

    @Override
    public void processMidiSignal(MidiSignal midiSignal) throws Exception {
        // Map the MIDI event and execute the appropriate actions

        // Only react to NOTE_ON events with a velocity higher than 0
        // TODO Disable velocity check in settings
        if (midiSignal.getCommand() != ShortMessage.NOTE_ON || midiSignal.getVelocity() == 0) {
            return;
        }

        // Search for and execute all required actions
        for (MidiControl midiControl : settingsService.getSettings().getMidiControlList()) {
            if (isActionMappingMatch(midiControl, midiSignal.getChannel(), midiSignal.getNote())) {
                controlActionExecutionService.execute(midiControl);
            }
        }
    }

}
