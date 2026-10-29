package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

/**
 * Triggered by a MIDI song select message (0xF3), which masters use to announce the song they are
 * about to play. Song select carries no channel, so the channel of the trigger is ignored.
 */
@Getter
@Setter
public class ActionTriggerMidiSongSelect extends ActionTriggerMidi {

    // If null -> all songs
    private Integer song;

}
