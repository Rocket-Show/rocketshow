package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.util.Arrays;
import java.util.Optional;

/**
 * Assemble incoming MIDI timecode (MTC) messages into absolute positions.
 * <p>
 * MTC transmits a position in two ways:
 * <ul>
 *     <li>Quarter-frame messages (0xF1) while the timecode is rolling. Each message carries one
 *     nibble, so a full position needs eight of them and therefore takes two frames to arrive. The
 *     transmitted value is the position at the <em>start</em> of that sequence, which means the real
 *     position is two frames further on in the direction of travel by the time it is complete
 *     (the same two frames {@link MidiTimecodeMessageFactory} adds when sending).</li>
 *     <li>A full-frame SysEx message while the timecode is parked, to locate to a new position.</li>
 * </ul>
 * Quarter-frame messages are sent in ascending piece order (0..7) when rolling forwards and in
 * descending order (7..0) when rolling backwards, which is how the direction is detected here.
 * <p>
 * This class is not thread-safe; it is fed from the MIDI input thread only.
 */
class MidiTimecodeDecoder {

    private static final int QUARTER_FRAME_STATUS = 0xF1;

    private static final int FULL_FRAME_LENGTH = 10;
    private static final int FULL_FRAME_UNIVERSAL_REALTIME_SYSEX = 0x7F;
    private static final int FULL_FRAME_SUB_ID_TIME_CODE = 0x01;
    private static final int FULL_FRAME_SUB_ID_FULL_MESSAGE = 0x01;

    /**
     * A decoded, absolute timecode position.
     *
     * @param positionMillis the absolute position of the timecode master
     * @param frameRate      the frame rate announced by the master
     * @param locate         true if this came from a full-frame message (a locate while parked)
     *                       rather than from a rolling quarter-frame sequence
     * @param direction      1 when rolling forwards, -1 when rolling backwards
     */
    record Result(long positionMillis, MidiTimecodeFrameRate frameRate, boolean locate, int direction) {
    }

    private final int[] nibbles = new int[8];
    private final boolean[] received = new boolean[8];

    private int lastPieceIndex = -1;
    private int direction = 1;

    /**
     * Offer a received MIDI message to the decoder. Returns a position as soon as one could be
     * assembled, which is at most once every two frames.
     */
    Optional<Result> process(MidiMessage midiMessage) {
        if (midiMessage instanceof ShortMessage shortMessage && shortMessage.getStatus() == QUARTER_FRAME_STATUS) {
            return processQuarterFrame(shortMessage.getData1());
        }

        if (midiMessage instanceof SysexMessage sysexMessage) {
            return processFullFrame(sysexMessage.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Forget any partially assembled position, e.g. because the timecode stopped.
     */
    void reset() {
        lastPieceIndex = -1;
        direction = 1;
        Arrays.fill(received, false);
    }

    private Optional<Result> processQuarterFrame(int data1) {
        int pieceIndex = (data1 >> 4) & 0x07;
        int value = data1 & 0x0F;

        if (lastPieceIndex < 0) {
            // First message of a new sequence: we cannot know the direction yet, so assume forwards
            // and wait for the sequence to complete.
            Arrays.fill(received, false);
        } else if (pieceIndex == (lastPieceIndex + 1) % 8) {
            direction = 1;
        } else if (pieceIndex == (lastPieceIndex + 7) % 8) {
            direction = -1;
        } else {
            // Out of sequence (messages were lost or the master re-located) -> start over
            Arrays.fill(received, false);
        }

        nibbles[pieceIndex] = value;
        received[pieceIndex] = true;
        lastPieceIndex = pieceIndex;

        // The sequence is complete on the last piece in the direction of travel
        if (pieceIndex != (direction > 0 ? 7 : 0)) {
            return Optional.empty();
        }

        for (boolean receivedPiece : received) {
            if (!receivedPiece) {
                return Optional.empty();
            }
        }

        Arrays.fill(received, false);

        int frame = nibbles[0] | (nibbles[1] << 4);
        int second = nibbles[2] | (nibbles[3] << 4);
        int minute = nibbles[4] | (nibbles[5] << 4);
        int hour = nibbles[6] | ((nibbles[7] & 0x01) << 4);
        MidiTimecodeFrameRate frameRate = MidiTimecodeFrameRate.fromMidiRateCode((nibbles[7] >> 1) & 0x03);

        MidiTimecodePosition position = new MidiTimecodePosition(hour, minute, second, frame);

        // The assembled value is where the master was when it started sending this sequence, two
        // frames ago in the direction it is travelling.
        long positionMillis = position.toMillis(frameRate)
                + Math.round(direction * 2 * 1000.0 / frameRate.getFramesPerSecond());

        return Optional.of(new Result(Math.max(0, positionMillis), frameRate, false, direction));
    }

    private Optional<Result> processFullFrame(byte[] data) {
        if (data == null || data.length != FULL_FRAME_LENGTH) {
            return Optional.empty();
        }

        if ((data[1] & 0xFF) != FULL_FRAME_UNIVERSAL_REALTIME_SYSEX
                || (data[3] & 0xFF) != FULL_FRAME_SUB_ID_TIME_CODE
                || (data[4] & 0xFF) != FULL_FRAME_SUB_ID_FULL_MESSAGE) {
            // Some other universal real-time SysEx message
            return Optional.empty();
        }

        int hourAndRate = data[5] & 0xFF;
        MidiTimecodeFrameRate frameRate = MidiTimecodeFrameRate.fromMidiRateCode((hourAndRate >> 5) & 0x03);
        MidiTimecodePosition position = new MidiTimecodePosition(
                hourAndRate & 0x1F,
                data[6] & 0x3F,
                data[7] & 0x3F,
                data[8] & 0x1F);

        // A locate restarts the quarter-frame sequence
        reset();

        return Optional.of(new Result(Math.max(0, position.toMillis(frameRate)), frameRate, true, 1));
    }

}
