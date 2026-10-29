package com.ascargon.rocketshow.midi;

/**
 * How an incoming (absolute) MIDI timecode position is mapped to a composition position when running
 * as a timecode slave.
 */
public enum MidiTimecodeSlaveMapping {

    // The incoming timecode drives the currently selected composition only. Its position is the
    // incoming timecode minus the composition's timecode start.
    COMPOSITION,

    // The incoming timecode drives the whole current set: each composition of the set occupies the
    // window starting at its timecode start and lasting for its duration. The composition matching
    // the incoming timecode is selected and played automatically.
    SET

}
