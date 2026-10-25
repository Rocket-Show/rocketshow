package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;

class MidiMapper {

    // Whether the parent mapping (in the end the global one) applies as well. A mapping overriding its parent
    // ignores it completely.
    private static boolean usesParent(MidiMapping midiMapping) {
        return midiMapping.getParent() != null && !Boolean.TRUE.equals(midiMapping.isOverrideParent());
    }

    private static int getChannelTo(int channelFrom, MidiMapping midiMapping) {
        // Search the channel map on the current mapping
        if (midiMapping.getChannelMap() != null) {
            for (int i = 0; i < midiMapping.getChannelMap().size(); i++) {
                if (midiMapping.getChannelMap().get(i).getChannelFrom() == channelFrom) {
                    return midiMapping.getChannelMap().get(i).getChannelTo();
                }
            }
        }

        // We haven't found a mapping -> search the parent
        if (usesParent(midiMapping)) {
            return getChannelTo(channelFrom, midiMapping.getParent());
        }

        // There is no mapping on the whole parent-chain -> channelTo =
        // channelFrom
        return channelFrom;
    }

    private static int getChannelOffset(MidiMapping midiMapping) {
        // The offsets of the mapping and its parents add up
        int channelOffset = midiMapping.getChannelOffset() == null ? 0 : midiMapping.getChannelOffset();

        if (usesParent(midiMapping)) {
            channelOffset += getChannelOffset(midiMapping.getParent());
        }

        return channelOffset;
    }

    private static int mapChannel(int channelFrom, MidiMapping midiMapping) {
        return getChannelTo(channelFrom, midiMapping) + getChannelOffset(midiMapping);
    }

    private static int getNoteOffset(MidiMapping midiMapping) {
        // The offsets of the mapping and its parents add up
        int noteOffset = midiMapping.getNoteOffset() == null ? 0 : midiMapping.getNoteOffset();

        if (usesParent(midiMapping)) {
            noteOffset += getNoteOffset(midiMapping.getParent());
        }

        return noteOffset;
    }

    public static void processMidiEvent(MidiMessage midiMessage, MidiMapping midiMapping) throws InvalidMidiDataException {
        // Transform the message according to the mapping

        // Only map short messages
        if (!(midiMessage instanceof ShortMessage)) {
            return;
        }

        ShortMessage shortMessage = (ShortMessage) midiMessage;

        // Only map NOTE_ON/NOTE_OFF events
        if (shortMessage.getCommand() != ShortMessage.NOTE_ON && shortMessage.getCommand() != ShortMessage.NOTE_OFF) {
            return;
        }

        // Map the channel
        shortMessage.setMessage(
                shortMessage.getCommand(),
                mapChannel(shortMessage.getChannel(), midiMapping),
                shortMessage.getData1(),
                shortMessage.getData2()
        );

        // Map the note (= Data1)
        shortMessage.setMessage(
                shortMessage.getCommand(),
                shortMessage.getChannel(),
                shortMessage.getData1() + getNoteOffset(midiMapping),
                shortMessage.getData2()
        );
    }

}
