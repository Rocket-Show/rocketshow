package com.ascargon.rocketshow.midi;

import javax.sound.midi.ShortMessage;

public class MidiMapper {

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
		if (midiMapping.getParent() != null) {
			if (!midiMapping.getParent().isOverrideParent()) {
				return getChannelTo(channelFrom, midiMapping.getParent());
			}
		}

		// There is no mapping on the whole parent-chain -> channelTo =
		// channelFrom
		return channelFrom;
	}

	private static int getChannelOffset(MidiMapping midiMapping) {
		// Check the current mapping
		if (midiMapping.getChannelOffset() != null) {
			return midiMapping.getChannelOffset();
		}

		// There is no offset on the current mapping -> check the parent
		if (midiMapping.getParent() != null) {
			if (!midiMapping.getParent().isOverrideParent()) {
				return getChannelOffset(midiMapping.getParent());
			}
		}

		// There is no offset on the whole parent-chain -> return default 0
		return 0;
	}

	private static int mapChannel(int channelFrom, MidiMapping midiMapping) {
		return getChannelTo(channelFrom, midiMapping) + getChannelOffset(midiMapping);
	}
	
	private static int getNoteOffset(MidiMapping midiMapping) {
		// Check the current mapping
		if (midiMapping.getNoteOffset() != null) {
			return midiMapping.getNoteOffset();
		}

		// There is no offset on the current mapping -> check the parent
		if (midiMapping.getParent() != null) {
			if (!midiMapping.getParent().isOverrideParent()) {
				return getNoteOffset(midiMapping.getParent());
			}
		}

		// There is no offset on the whole parent-chain -> return default 0
		return 0;
	}
	
	public static void processMidiEvent(MidiSignal midiSignal, MidiMapping midiMapping) {
		// Transform the message according to the mapping

		// Only react to NOTE_ON/NOTE_OFF events
		if (midiSignal.getCommand() != ShortMessage.NOTE_ON && midiSignal.getCommand() != ShortMessage.NOTE_OFF) {
			return;
		}

		// Map the channel
		midiSignal.setChannel(mapChannel(midiSignal.getChannel(), midiMapping));
		
		// Map the note
		midiSignal.setNote(midiSignal.getNote() + getNoteOffset(midiMapping));
	}

}
