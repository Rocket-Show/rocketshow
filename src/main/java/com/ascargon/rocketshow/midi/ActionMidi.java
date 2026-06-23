package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActionMidi extends Action {

    private MidiSignal midiSignal;

    @Override
    public ActionType getType() {
        return ActionType.MIDI;
    }
}
