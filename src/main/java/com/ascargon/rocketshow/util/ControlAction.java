package com.ascargon.rocketshow.util;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public abstract class ActionControl {
	
	// Actions to be executed (e.g. by MIDI control or Raspberry GPIO events)
	public enum Action {
		PLAY, TOGGLE_PLAY, PAUSE, NEXT_COMPOSITION, PREVIOUS_COMPOSITION, STOP, SET_COMPOSITION_INDEX, REBOOT, SELECT_COMPOSITION_BY_NAME, SELECT_COMPOSITION_BY_NAME_AND_PLAY
	}
	
	// The Action to be executed
	private Action action;

	// The composition to be selected, if such an action is executed
	private String selectComposition;
	
	// Should this action apply to a remote device?
	private List<String> remoteDeviceNames = new ArrayList<String>();
	
	// Should this action apply locally?
	private boolean executeLocally = true;
	
	@XmlElement
	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
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
