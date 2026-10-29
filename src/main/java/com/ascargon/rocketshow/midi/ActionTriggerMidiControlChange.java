package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionTriggerMidiControlChange extends ActionTriggerMidi {

    // The controller number. If null -> all controllers
    private Integer controller;

    // The controller value. If null -> any value
    private Integer value;

}
