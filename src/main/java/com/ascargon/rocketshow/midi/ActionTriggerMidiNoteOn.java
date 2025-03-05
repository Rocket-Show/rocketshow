package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionTriggerMidiNoteOn extends ActionTriggerMidi {

    // The note, this action should be triggered. If null -> all notes
    private Integer note;

}
