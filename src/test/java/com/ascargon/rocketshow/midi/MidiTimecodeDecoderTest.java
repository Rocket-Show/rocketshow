package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiTimecodeDecoderTest {

    /**
     * Build the eight quarter-frame messages a timecode master sends for one position, in the
     * standard order (all eight carry the same value).
     */
    private static List<ShortMessage> createQuarterFrames(int hour, int minute, int second, int frame,
                                                          MidiTimecodeFrameRate frameRate) throws InvalidMidiDataException {
        int[] values = {
                frame & 0x0F,
                (frame >> 4) & 0x01,
                second & 0x0F,
                (second >> 4) & 0x03,
                minute & 0x0F,
                (minute >> 4) & 0x03,
                hour & 0x0F,
                ((frameRate.getMidiRateCode() & 0x03) << 1) | ((hour >> 4) & 0x01)
        };

        List<ShortMessage> messages = new ArrayList<>();

        for (int messageType = 0; messageType < 8; messageType++) {
            ShortMessage message = new ShortMessage();
            message.setMessage(0xF1, (messageType << 4) | values[messageType], 0);
            messages.add(message);
        }

        return messages;
    }

    private static Optional<MidiTimecodeDecoder.Result> process(MidiTimecodeDecoder decoder, List<? extends MidiMessage> messages) {
        Optional<MidiTimecodeDecoder.Result> result = Optional.empty();

        for (MidiMessage message : messages) {
            Optional<MidiTimecodeDecoder.Result> decoded = decoder.process(message);

            if (decoded.isPresent()) {
                result = decoded;
            }
        }

        return result;
    }

    @Test
    void decodesAQuarterFrameSequenceTwoFramesAheadOfTheTransmittedValue() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        Optional<MidiTimecodeDecoder.Result> result = process(decoder, createQuarterFrames(1, 2, 3, 4, MidiTimecodeFrameRate.FPS_25));

        assertTrue(result.isPresent());
        // 01:02:03:04 at 25fps is 3'723'160ms, plus the two frames the sequence took to arrive
        assertEquals(3_723_160 + 80, result.get().positionMillis());
        assertEquals(MidiTimecodeFrameRate.FPS_25, result.get().frameRate());
        assertEquals(1, result.get().direction());
        assertFalse(result.get().locate());
    }

    @Test
    void reportsNothingBeforeTheSequenceIsComplete() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();
        List<ShortMessage> messages = createQuarterFrames(1, 2, 3, 4, MidiTimecodeFrameRate.FPS_25);

        for (ShortMessage message : messages.subList(0, 7)) {
            assertTrue(decoder.process(message).isEmpty());
        }

        assertTrue(decoder.process(messages.get(7)).isPresent());
    }

    @Test
    void decodesADescendingSequenceAsRunningBackwards() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        List<ShortMessage> messages = new ArrayList<>(createQuarterFrames(1, 2, 3, 4, MidiTimecodeFrameRate.FPS_25));
        Collections.reverse(messages);

        Optional<MidiTimecodeDecoder.Result> result = process(decoder, messages);

        assertTrue(result.isPresent());
        assertEquals(-1, result.get().direction());
        // Running backwards, the master has moved two frames the other way
        assertEquals(3_723_160 - 80, result.get().positionMillis());
    }

    @Test
    void ignoresAnIncompleteSequenceAndLocksOnToTheNextCompleteOne() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        // A sequence that starts in the middle (the slave was switched on while the master was
        // already rolling) must not be reported
        List<ShortMessage> partial = createQuarterFrames(1, 2, 3, 4, MidiTimecodeFrameRate.FPS_25).subList(3, 8);
        assertTrue(process(decoder, partial).isEmpty());

        Optional<MidiTimecodeDecoder.Result> result = process(decoder, createQuarterFrames(1, 2, 3, 6, MidiTimecodeFrameRate.FPS_25));

        assertTrue(result.isPresent());
        assertEquals(3_723_240 + 80, result.get().positionMillis());
    }

    @Test
    void decodesAFullFrameMessageAsALocate() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        SysexMessage message = MidiTimecodeMessageFactory.createFullFrameMessage(3_723_456, MidiTimecodeFrameRate.FPS_30);
        Optional<MidiTimecodeDecoder.Result> result = decoder.process(message);

        assertTrue(result.isPresent());
        assertTrue(result.get().locate());
        // A full frame carries no offset, so it decodes back to its own frame (01:02:03:14 at 30fps)
        assertEquals(3_723_467, result.get().positionMillis());
        assertEquals(MidiTimecodeFrameRate.FPS_30, result.get().frameRate());
    }

    @Test
    void ignoresOtherSysexMessages() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        byte[] data = {(byte) 0xF0, 0x7E, 0x7F, 0x06, 0x01, (byte) 0xF7};
        SysexMessage message = new SysexMessage();
        message.setMessage(data, data.length);

        assertTrue(decoder.process(message).isEmpty());
    }

    @Test
    void decodesDropFrameTimecodeBackToTheEncodedPosition() throws Exception {
        MidiTimecodeDecoder decoder = new MidiTimecodeDecoder();

        // Encode a position as a full frame and decode it again - drop-frame numbering has to be
        // reversed exactly, or the position drifts by two frames per minute
        SysexMessage message = MidiTimecodeMessageFactory.createFullFrameMessage(600_000, MidiTimecodeFrameRate.FPS_29_97_DROP);
        Optional<MidiTimecodeDecoder.Result> result = decoder.process(message);

        assertTrue(result.isPresent());
        assertEquals(600_000, result.get().positionMillis());
    }

}
