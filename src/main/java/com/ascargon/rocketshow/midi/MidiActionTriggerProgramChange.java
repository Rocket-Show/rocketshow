package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MidiActionTriggerProgramChange extends MidiActionTrigger {

    // If null -> all programs
    private Integer program;

}
