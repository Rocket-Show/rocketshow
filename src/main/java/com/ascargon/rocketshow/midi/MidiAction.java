package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

import javax.sound.midi.ShortMessage;

@Getter
@Setter
public class MidiAction extends Action {

    private MidiSignal midiSignal;

    @Override
    public ActionType getType() {
        return ActionType.MIDI;
    }
}
