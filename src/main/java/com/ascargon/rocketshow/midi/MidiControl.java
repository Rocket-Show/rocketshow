package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Map one specific MIDI event to an action.
 *
 * @author Moritz Vieli
 */
@XmlRootElement
public class MidiControl {

	public enum MidiAction {
		PLAY, TOGGLE_PLAY, PAUSE, NEXT_COMPOSITION, PREVIOUS_COMPOSITION, STOP, SET_COMPOSITION_INDEX, REBOOT, SELECT_COMPOSITION_BY_NAME, SELECT_COMPOSITION_BY_NAME_AND_PLAY
	}

	private MidiAction action;

	private String selectComposition;

	// If null -> all channels
	private Integer channelFrom;

	// The note, this action should be triggered. If null -> all notes
	private Integer noteFrom;

	// Should this action apply to a remote device?
	private List<String> remoteDeviceNames = new ArrayList<String>();

	// Should this action apply locally?
	private boolean executeLocally = true;

	@XmlElement
	public MidiAction getAction() {
		return action;
	}

	public void setAction(MidiAction action) {
		this.action = action;
	}

	@XmlElement
	public String getSelectComposition() {
		return selectComposition;
	}

	public void setSelectComposition(String selectComposition) {
		this.selectComposition = selectComposition;
	}

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

	@XmlElement
	public boolean isExecuteLocally() {
		return executeLocally;
	}

	public void setExecuteLocally(boolean executeLocally) {
		this.executeLocally = executeLocally;
	}

	@XmlElement(name = "remoteDevice")
	@XmlElementWrapper(name = "remoteDeviceList")
	public List<String> getRemoteDeviceNames() {
		return remoteDeviceNames;
	}

	public void setRemoteDeviceNames(List<String> remoteDeviceNames) {
		this.remoteDeviceNames = remoteDeviceNames;
	}

}
