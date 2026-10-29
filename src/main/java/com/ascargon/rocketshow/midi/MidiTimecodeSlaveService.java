package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiMessage;

/**
 * Follow the MIDI timecode (MTC) received on the MIDI input device, so this device plays in sync
 * with an external timecode master such as a DAW or a playback rig.
 */
public interface MidiTimecodeSlaveService {

    /**
     * Offer a message received from the MIDI input device. Ignored unless the device is configured
     * as a timecode slave.
     */
    void processMidiMessage(MidiMessage midiMessage);

    /**
     * True while an incoming timecode is actually being received and followed.
     */
    boolean isLocked();

    /**
     * The absolute position of the timecode master in milliseconds, or -1 if no timecode is being
     * received.
     */
    long getTimecodeMillis();

}
