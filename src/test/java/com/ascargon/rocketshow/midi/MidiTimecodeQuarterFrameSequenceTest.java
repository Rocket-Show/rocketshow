package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sending and the receiving side have to agree, so these decode what the master sends with the
 * same decoder a slave uses.
 */
class MidiTimecodeQuarterFrameSequenceTest {

    private final static MidiTimecodeFrameRate FRAME_RATE = MidiTimecodeFrameRate.FPS_25;

    // A quarter frame at 25fps
    private final static long QUARTER_FRAME_MILLIS = 10;

    /**
     * Send one complete sequence, with the playback position advancing by a quarter frame between
     * messages exactly as it does while playing, and return what a receiver makes of it.
     */
    private static long sendAndDecode(long startPositionMillis) throws Exception {
        MidiTimecodeQuarterFrameSequence sequence = new MidiTimecodeQuarterFrameSequence();
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        Optional<MidiTimecodeDecoder.Result> result = Optional.empty();

        for (int messageType = 0; messageType < 8; messageType++) {
            ShortMessage message = sequence.next(startPositionMillis + messageType * QUARTER_FRAME_MILLIS, FRAME_RATE);
            Optional<MidiTimecodeDecoder.Result> decoded = decoder.process(message);

            if (decoded.isPresent()) {
                result = decoded;
            }
        }

        assertTrue(result.isPresent(), "The sequence should decode to a position");

        return result.get().positionMillis();
    }

    @Test
    void decodesToWhereTheMasterIsWhenTheSequenceIsComplete() throws Exception {
        // 01:02:03:04, and two frames (80ms) later the sequence has arrived - which is exactly the
        // position the receiver has to end up at, not two frames beyond it
        assertEquals(3_723_160 + 80, sendAndDecode(3_723_160));
    }

    @Test
    void doesNotReReadThePositionWhileTheSequenceIsRunning() throws Exception {
        // 01:02:03:24 is the last frame of its second, so re-reading the position halfway through
        // the sequence would mix the frame nibbles of :24 with the second nibbles of the next
        // second and decode to a position a whole second away
        assertEquals(3_723_960 + 80, sendAndDecode(3_723_960));
    }

}
