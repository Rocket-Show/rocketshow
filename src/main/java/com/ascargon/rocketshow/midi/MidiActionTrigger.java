package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.ActionTrigger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MidiActionTrigger extends ActionTrigger {

    public enum MidiActionTriggerType {
        NOTE_ON,
        PROGRAM_CHANGE
    }

    private MidiActionTriggerType midiActionTriggerType;

    // If null -> all channels
    private Integer channel;

    // The note, this action should be triggered. If null -> all notes
    private Integer note;

    // If null -> all programs
    private Integer program;

}
