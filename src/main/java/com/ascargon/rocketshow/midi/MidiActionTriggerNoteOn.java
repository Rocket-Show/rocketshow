package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MidiActionTriggerNoteOn extends MidiActionTrigger {

    // The note, this action should be triggered. If null -> all notes
    private Integer note;

}
