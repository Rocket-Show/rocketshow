package com.ascargon.rocketshow.midi;

import lombok.Getter;
import lombok.Setter;

/**
 * Triggered by one of the MIDI system real-time transport messages, which a master sends to run the
 * transport of everything on the MIDI line at once. They carry no channel, so the channel of the
 * trigger is ignored.
 */
@Getter
@Setter
public class ActionTriggerMidiSystemRealTime extends ActionTriggerMidi {

    public enum SystemRealTimeType {
        // Start playing from the beginning (0xFA)
        START,
        // Resume playing from the current position (0xFB)
        CONTINUE,
        // Stop playing (0xFC)
        STOP
    }

    private SystemRealTimeType systemRealTimeType;

}
