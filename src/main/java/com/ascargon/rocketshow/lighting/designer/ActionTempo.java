package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

/**
 * The tempo from outside (see TempoService), e.g. tapped on a pad of a MIDI controller or set at a position of a
 * composition.
 */
@Getter
@Setter
public class ActionTempo extends Action {

    public enum TempoActionType {
        // a beat tapped
        TAP,
        // now is the first beat of a bar
        BEAT_ONE,
        // the tempo set to beatsPerMinute
        SET,
        // the tempo playing halved or doubled
        HALVE,
        DOUBLE,
        // the tempo playing a beat per minute faster or slower
        FASTER,
        SLOWER,
        // back to the programmed tempo of the composition or the project playing
        PROJECT
    }

    private TempoActionType tempoActionType = TempoActionType.TAP;

    // the tempo set (SET)
    private double beatsPerMinute = 120;

    @Override
    public ActionType getType() {
        return ActionType.TEMPO;
    }

}
