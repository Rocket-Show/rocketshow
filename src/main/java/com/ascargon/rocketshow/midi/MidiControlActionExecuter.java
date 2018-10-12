package com.ascargon.rocketshow.midi;

import javax.sound.midi.ShortMessage;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

public class MidiControlActionExecuter {

	final static Logger logger = Logger.getLogger(MidiControlActionExecuter.class);

	private Manager manager;

	public MidiControlActionExecuter(Manager manager) {
		this.manager = manager;
	}

	/**
	 * Does this action mapping match to the current MIDI message and should the
	 * action be executed?
	 * 
	 * @param actionMapping
	 * @param channel
	 * @param note
	 * @return
	 */
	private boolean isActionMappingMatch(MidiControl midiControl, int channel, int note) {
		if ((midiControl.getChannelFrom() == null || midiControl.getChannelFrom() == channel)
				&& (midiControl.getNoteFrom() == null || midiControl.getNoteFrom() == note)) {
			return true;
		}

		return false;
	}

	public void processMidiSignal(MidiSignal midiSignal) throws Exception {
		// Map the MIDI event and execute the appropriate actions

		// Only react to NOTE_ON events with a velocity higher than 0
		// TODO Disable velocity check in settings
		if (midiSignal.getCommand() != ShortMessage.NOTE_ON || midiSignal.getVelocity() == 0) {
			return;
		}

		// Search for and execute all required actions
		for (MidiControl midiControl : manager.getSettings().getMidiControlList()) {
			if (isActionMappingMatch(midiControl, midiSignal.getChannel(), midiSignal.getNote())) {
				manager.getControlActionExecuter().execute(midiControl);
			}
		}
	}

}
