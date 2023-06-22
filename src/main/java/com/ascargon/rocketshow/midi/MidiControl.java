package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.util.ControlAction;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Map one specific MIDI event to an action.
 *
 * @author Moritz Vieli
 */
@XmlRootElement
public class MidiControl extends ControlAction {

	// If null -> all channels
	private Integer channelFrom;

	// The note, this action should be triggered. If null -> all notes
	private Integer noteFrom;

	public Integer getChannelFrom() {
		return channelFrom;
	}

	public void setChannelFrom(Integer channelFrom) {
		this.channelFrom = channelFrom;
	}

	public Integer getNoteFrom() {
		return noteFrom;
	}

	public void setNoteFrom(Integer noteFrom) {
		this.noteFrom = noteFrom;
	}

}
