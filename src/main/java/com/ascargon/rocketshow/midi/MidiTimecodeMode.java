package com.ascargon.rocketshow.midi;

/**
 * How this device deals with MIDI timecode (MTC).
 */
public enum MidiTimecodeMode {

    // No MIDI timecode is sent or received
    OFF,

    // Send MIDI timecode based on the local playback position (this device is the timecode master)
    MASTER,

    // Follow the MIDI timecode received on the MIDI input device (this device is a timecode slave)
    SLAVE

}
