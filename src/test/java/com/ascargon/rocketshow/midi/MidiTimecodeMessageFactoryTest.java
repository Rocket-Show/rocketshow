package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiTimecodeMessageFactoryTest {

    @Test
    void createQuarterFrameMessageEncodesMessageTypeAndNibble() throws Exception {
        ShortMessage message = MidiTimecodeMessageFactory.createQuarterFrameMessage(1_234, MidiTimecodeFrameRate.FPS_30, 0);

        assertEquals(0xF1, message.getStatus());
        assertEquals(0x09, message.getData1());
    }

    @Test
    void createQuarterFrameMessageEncodesRateInHourHighNibble() throws Exception {
        ShortMessage message = MidiTimecodeMessageFactory.createQuarterFrameMessage(0, MidiTimecodeFrameRate.FPS_25, 7);

        assertEquals(0xF1, message.getStatus());
        assertEquals(0x72, message.getData1());
    }

    @Test
    void createFullFrameMessageEncodesPositionAndRate() throws Exception {
        SysexMessage message = MidiTimecodeMessageFactory.createFullFrameMessage(3_723_456, MidiTimecodeFrameRate.FPS_30);

        assertArrayEquals(new byte[]{
                (byte) 0xF0,
                0x7F,
                0x7F,
                0x01,
                0x01,
                0x61,
                0x02,
                0x03,
                0x0E,
                (byte) 0xF7
        }, message.getMessage());
    }
}
