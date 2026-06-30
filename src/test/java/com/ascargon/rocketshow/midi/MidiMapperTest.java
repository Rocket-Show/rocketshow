package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiMapperTest {

    private ShortMessage noteOn(int channel, int note, int velocity) throws Exception {
        return new ShortMessage(ShortMessage.NOTE_ON, channel, note, velocity);
    }

    private ChannelMapping channelMapping(int from, int to) {
        ChannelMapping channelMapping = new ChannelMapping();
        channelMapping.setChannelFrom(from);
        channelMapping.setChannelTo(to);
        return channelMapping;
    }

    @Test
    void mapsChannelAccordingToChannelMap() throws Exception {
        MidiMapping mapping = new MidiMapping();
        mapping.setChannelMap(List.of(channelMapping(0, 5)));

        ShortMessage message = noteOn(0, 60, 100);
        MidiMapper.processMidiEvent(message, mapping);

        assertEquals(5, message.getChannel());
        assertEquals(60, message.getData1());
    }

    @Test
    void appliesChannelOffset() throws Exception {
        MidiMapping mapping = new MidiMapping();
        mapping.setChannelOffset(2);

        ShortMessage message = noteOn(1, 60, 100);
        MidiMapper.processMidiEvent(message, mapping);

        // No channel map -> channelTo == channelFrom (1), plus the offset (2)
        assertEquals(3, message.getChannel());
    }

    @Test
    void appliesNoteOffset() throws Exception {
        MidiMapping mapping = new MidiMapping();
        mapping.setNoteOffset(12);

        ShortMessage message = noteOn(0, 60, 100);
        MidiMapper.processMidiEvent(message, mapping);

        assertEquals(72, message.getData1());
    }

    @Test
    void inheritsChannelMapFromParentWhenNotOverridden() throws Exception {
        MidiMapping parent = new MidiMapping();
        parent.setChannelMap(List.of(channelMapping(0, 7)));

        MidiMapping child = new MidiMapping();
        child.setParent(parent);

        ShortMessage message = noteOn(0, 60, 100);
        MidiMapper.processMidiEvent(message, child);

        assertEquals(7, message.getChannel());
    }

    @Test
    void ignoresParentMappingWhenParentOverridesItsChildren() throws Exception {
        MidiMapping parent = new MidiMapping();
        parent.setChannelMap(List.of(channelMapping(0, 7)));
        parent.setOverrideParent(true);

        MidiMapping child = new MidiMapping();
        child.setParent(parent);

        ShortMessage message = noteOn(0, 60, 100);
        MidiMapper.processMidiEvent(message, child);

        // Parent declares itself as overriding -> its mapping must not be consulted
        assertEquals(0, message.getChannel());
    }

    @Test
    void leavesNonNoteMessagesUnchanged() throws Exception {
        MidiMapping mapping = new MidiMapping();
        mapping.setChannelOffset(3);
        mapping.setNoteOffset(12);

        ShortMessage message = new ShortMessage(ShortMessage.CONTROL_CHANGE, 0, 10, 100);
        MidiMapper.processMidiEvent(message, mapping);

        assertEquals(0, message.getChannel());
        assertEquals(10, message.getData1());
    }

    @Test
    void leavesNonShortMessagesUnchanged() throws Exception {
        MidiMapping mapping = new MidiMapping();
        mapping.setNoteOffset(12);

        byte[] data = {(byte) 0xF0, 0x7E, (byte) 0xF7};
        SysexMessage message = new SysexMessage(data, data.length);

        // Must not throw and must not modify the message
        MidiMapper.processMidiEvent(message, mapping);

        assertEquals(3, message.getLength());
    }
}
