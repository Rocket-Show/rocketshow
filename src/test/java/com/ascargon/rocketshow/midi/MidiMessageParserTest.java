package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiMessageParserTest {

    @Test
    void offerByteAssemblesThreeByteNoteOn() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        // Status and first data byte do not complete the message yet
        assertTrue(parser.offerByte((byte) 0x90).isEmpty());
        assertTrue(parser.offerByte((byte) 0x3C).isEmpty());

        Optional<MidiMessage> result = parser.offerByte((byte) 0x64);

        assertTrue(result.isPresent());
        ShortMessage message = assertInstanceOf(ShortMessage.class, result.get());
        assertEquals(ShortMessage.NOTE_ON, message.getCommand());
        assertEquals(0, message.getChannel());
        assertEquals(0x3C, message.getData1());
        assertEquals(0x64, message.getData2());
    }

    @Test
    void offerByteAssemblesTwoByteProgramChange() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        assertTrue(parser.offerByte((byte) 0xC0).isEmpty());
        Optional<MidiMessage> result = parser.offerByte((byte) 0x05);

        assertTrue(result.isPresent());
        ShortMessage message = assertInstanceOf(ShortMessage.class, result.get());
        assertEquals(ShortMessage.PROGRAM_CHANGE, message.getCommand());
        assertEquals(0x05, message.getData1());
    }

    @Test
    void offerByteEmitsSingleByteRealTimeMessageImmediately() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        // 0xF8 = timing clock, a one-byte real-time message
        Optional<MidiMessage> result = parser.offerByte((byte) 0xF8);

        assertTrue(result.isPresent());
        assertEquals(0xF8, result.get().getStatus());
        assertEquals(1, result.get().getLength());
    }

    @Test
    void offerByteAssemblesTwoByteQuarterFrame() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        // 0xF1 = MIDI timecode quarter frame (2 bytes)
        assertTrue(parser.offerByte((byte) 0xF1).isEmpty());
        Optional<MidiMessage> result = parser.offerByte((byte) 0x09);

        assertTrue(result.isPresent());
        assertEquals(0xF1, result.get().getStatus());
    }

    @Test
    void offerByteAccumulatesSysExUntilEndMarker() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        byte[] sysEx = {(byte) 0xF0, 0x7E, 0x00, 0x06, 0x01, (byte) 0xF7};

        for (int i = 0; i < sysEx.length - 1; i++) {
            assertTrue(parser.offerByte(sysEx[i]).isEmpty(), "byte " + i + " should not complete the message");
        }

        Optional<MidiMessage> result = parser.offerByte(sysEx[sysEx.length - 1]);

        assertTrue(result.isPresent());
        SysexMessage message = assertInstanceOf(SysexMessage.class, result.get());
        assertArrayEquals(sysEx, message.getMessage());
    }

    @Test
    void offerBytesReturnsTheLastCompletedMessage() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        // Two complete NOTE_ON messages in a single chunk
        Optional<MidiMessage> result = parser.offerBytes(new byte[]{
                (byte) 0x90, 0x3C, 0x64,
                (byte) 0x90, 0x3E, 0x40
        });

        assertTrue(result.isPresent());
        ShortMessage message = assertInstanceOf(ShortMessage.class, result.get());
        assertEquals(0x3E, message.getData1());
        assertEquals(0x40, message.getData2());
    }

    @Test
    void offerBytesIsEmptyForIncompleteMessage() throws Exception {
        MidiMessageParser parser = new MidiMessageParser();

        assertFalse(parser.offerBytes(new byte[]{(byte) 0x90, 0x3C}).isPresent());
    }

    @Test
    void createMidiMessageFromBytesBuildsNoteOff() throws Exception {
        MidiMessage message = MidiMessageParser.createMidiMessageFromBytes(new byte[]{(byte) 0x80, 0x3C, 0x00}, 3);

        ShortMessage shortMessage = assertInstanceOf(ShortMessage.class, message);
        assertEquals(ShortMessage.NOTE_OFF, shortMessage.getCommand());
        assertEquals(0x3C, shortMessage.getData1());
    }
}
