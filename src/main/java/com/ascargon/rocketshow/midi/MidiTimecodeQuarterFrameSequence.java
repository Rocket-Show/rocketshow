package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

/**
 * Produces the quarter-frame messages of an outgoing MIDI timecode stream, in order.
 * <p>
 * A position needs eight messages and therefore two frames to transmit, and all eight have to carry
 * the same value: the position the sequence started at. The receiver knows that value is two frames
 * old by the time it has all eight and adds them back itself, so this must not read the playback
 * position again for every message - that would both make the receiver two frames early and let the
 * nibbles come from different moments, which garbles the position across a second or minute boundary.
 */
class MidiTimecodeQuarterFrameSequence {

    private int messageType = 0;
    private MidiTimecodePosition position;

    /**
     * The next message of the stream, reading the playback position only at the start of a sequence.
     */
    ShortMessage next(long positionMillis, MidiTimecodeFrameRate frameRate) throws InvalidMidiDataException {
        if (messageType == 0) {
            position = MidiTimecodeMessageFactory.getTimecodePosition(positionMillis, frameRate);
        }

        ShortMessage message = MidiTimecodeMessageFactory.createQuarterFrameMessage(position, frameRate, messageType);
        messageType = (messageType + 1) % 8;

        return message;
    }

}
