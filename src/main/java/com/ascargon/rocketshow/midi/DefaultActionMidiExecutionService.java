package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import com.ascargon.rocketshow.lighting.designer.TempoService;
import org.springframework.context.annotation.Lazy;
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
    private final static int CLOCK_STATUS = 0xF8;
    private final static int SONG_POSITION_STATUS = 0xF2;


    private final SettingsService settingsService;
    private final ActionExecutionService actionExecutionService;
    private final ScenePlayerService scenePlayerService;
    private final TempoService tempoService;

    // The scene player is loaded lazily: it tells the apps the state of the device, which needs much of the rest
    public DefaultActionMidiExecutionService(SettingsService settingsService, ActionExecutionService actionExecutionService, @Lazy ScenePlayerService scenePlayerService, @Lazy TempoService tempoService) {
        this.settingsService = settingsService;
        this.actionExecutionService = actionExecutionService;
        this.scenePlayerService = scenePlayerService;
        this.tempoService = tempoService;
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

    // Whether the message lets go of the key or button of a trigger: a note off (or a note on with velocity 0) of its
    // note, a control change to 0 of a controller it is triggered by with another value
    private boolean isReleaseOf(ActionTriggerMidi actionTriggerMidi, ShortMessage shortMessage) {
        if (actionTriggerMidi.getChannel() != null && actionTriggerMidi.getChannel() != shortMessage.getChannel()) {
            return false;
        }

        boolean noteOff = shortMessage.getCommand() == ShortMessage.NOTE_OFF || (shortMessage.getCommand() == ShortMessage.NOTE_ON && shortMessage.getData2() == 0);

        if (noteOff && actionTriggerMidi instanceof ActionTriggerMidiNoteOn actionTriggerMidiNoteOn) {
            return actionTriggerMidiNoteOn.getNote() == null || actionTriggerMidiNoteOn.getNote() == shortMessage.getData1();
        }

        if (shortMessage.getCommand() == ShortMessage.CONTROL_CHANGE && shortMessage.getData2() == 0 && actionTriggerMidi instanceof ActionTriggerMidiControlChange actionTriggerMidiControlChange) {
            return (actionTriggerMidiControlChange.getController() == null || actionTriggerMidiControlChange.getController() == shortMessage.getData1())
                    && actionTriggerMidiControlChange.getValue() != null && actionTriggerMidiControlChange.getValue() != 0;
        }

        return false;
    }

    @Override
    public void processMidiSignal(ShortMessage shortMessage) throws Exception {
        // a MIDI clock gives the tempo from outside and where its beats are, once started (see TempoService)
        int status = shortMessage.getStatus();

        if (status == CLOCK_STATUS) {
            tempoService.midiClockTick(System.nanoTime());
            return;
        }

        if (status == SONG_POSITION_STATUS) {
            tempoService.midiSongPosition(shortMessage.getData1() | (shortMessage.getData2() << 7));
            return;
        }

        if (status == START_STATUS) {
            tempoService.midiClockStart();
        } else if (status == CONTINUE_STATUS) {
            tempoService.midiClockContinue();
        } else if (status == STOP_STATUS) {
            tempoService.midiClockStop();
        }

        // a scene waiting for its key, pad or button takes the message, which triggers nothing else then
        if (scenePlayerService.learnMidi(shortMessage)) {
            return;
        }

        for (ActionTriggerMidi actionTriggerMidi : settingsService.getSettings().getActionTriggerMidiList()) {
            if (isReleaseOf(actionTriggerMidi, shortMessage)) {
                actionExecutionService.releaseFromTrigger(actionTriggerMidi);
            }
        }

        // Map the MIDI event and executeFromTrigger the appropriate actions

        // Search for and executeFromTrigger all required actions
        for (ActionTriggerMidi actionTriggerMidi : settingsService.getSettings().getActionTriggerMidiList()) {
            if (isActionMappingMatch(actionTriggerMidi, shortMessage)) {
                actionExecutionService.executeFromTrigger(actionTriggerMidi);
            }
        }
    }

}
