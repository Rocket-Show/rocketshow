package com.ascargon.rocketshow.util;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ControlAction {

	// Actions to be executed (e.g. by MIDI control or Raspberry GPIO events)
	public enum Action {
		PLAY, PLAY_AS_SAMPLE, TOGGLE_PLAY, PAUSE, NEXT_COMPOSITION, PREVIOUS_COMPOSITION, STOP, SET_COMPOSITION_INDEX, REBOOT, SELECT_COMPOSITION_BY_NAME, SELECT_COMPOSITION_BY_NAME_AND_PLAY
	}

	// The Action to be executed
	private Action action;

	// The composition to be selected or player, if such an action is executed
	private String compositionName;

	// Should this action apply to a remote device?
	private List<String> remoteDeviceNames = new ArrayList<>();

	// Should this action apply locally?
	private boolean executeLocally = true;

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}

	public String getCompositionName() {
		return compositionName;
	}

	public void setCompositionName(String compositionName) {
		this.compositionName = compositionName;
	}

	public boolean isExecuteLocally() {
		return executeLocally;
	}

	public void setExecuteLocally(boolean executeLocally) {
		this.executeLocally = executeLocally;
	}

	@XmlElement(name = "remoteDevice")
	@XmlElementWrapper(name = "remoteDeviceList")
	@SuppressWarnings("WeakerAccess")
	public List<String> getRemoteDeviceNames() {
		return remoteDeviceNames;
	}

	@SuppressWarnings("unused")
	public void setRemoteDeviceNames(List<String> remoteDeviceNames) {
		this.remoteDeviceNames = remoteDeviceNames;
	}

}
