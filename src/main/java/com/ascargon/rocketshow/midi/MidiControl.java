package com.ascargon.rocketshow.midi;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.ascargon.rocketshow.util.ControlAction;

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

	@XmlElement
	public Integer getChannelFrom() {
		return channelFrom;
	}

	public void setChannelFrom(Integer channelFrom) {
		this.channelFrom = channelFrom;
	}

	@XmlElement
	public Integer getNoteFrom() {
		return noteFrom;
	}

	public void setNoteFrom(Integer noteFrom) {
		this.noteFrom = noteFrom;
	}

}
