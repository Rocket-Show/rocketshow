package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionTriggerMidiProgramChange extends ActionTriggerMidi {

    // If null -> all programs
    private Integer program;

}
