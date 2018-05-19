package com.ascargon.rocketshow.midi;

import java.io.IOException;

import javax.sound.midi.ShortMessage;

import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.RemoteDevice;

public class Midi2ActionConverter {

	final static Logger logger = Logger.getLogger(Midi2ActionConverter.class);

	private Manager manager;

	public Midi2ActionConverter(Manager manager) {
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
	private boolean isActionMappingMatch(MidiControl actionMapping, int channel, int note) {
		if ((actionMapping.getChannelFrom() == null || actionMapping.getChannelFrom() == channel)
				&& (actionMapping.getNoteFrom() == null || actionMapping.getNoteFrom() == note)) {
			return true;
		}

		return false;
	}

	private void executeActionOnRemoteDevice(MidiControl midiControl, RemoteDevice remoteDevice)
			throws ClientProtocolException, IOException {

		switch (midiControl.getAction()) {
		case PLAY:
			remoteDevice.play();
			break;
		case PAUSE:
			remoteDevice.pause();
			break;
		case TOGGLE_PLAY:
			remoteDevice.togglePlay();
			break;
		case RESUME:
			remoteDevice.resume();
			break;
		case STOP:
			remoteDevice.stop();
			break;
		case NEXT_COMPOSITION:
			remoteDevice.setNextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			remoteDevice.setPreviousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			remoteDevice.setCompositionName(midiControl.getSelectComposition());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			remoteDevice.setCompositionName(midiControl.getSelectComposition());
			remoteDevice.play();
			break;
		case SET_COMPOSITION_INDEX:
			remoteDevice.setCompositionIndex(manager.getCurrentSet().getCurrentCompositionIndex());
			break;
		case REBOOT:
			remoteDevice.reboot();
			break;
		default:
			logger.warn("Action '" + midiControl.getAction().toString() + "' is unknown for remote devices and cannot be executed");
			break;
		}
	}

	private void executeActionLocally(MidiControl midiControl) throws Exception {
		// Execute the action locally
		logger.info("Execute action from MIDI event");
		
		switch (midiControl.getAction()) {
		case PLAY:
			manager.getPlayer().play();
			break;
		case PAUSE:
			manager.getPlayer().pause();
			break;
		case TOGGLE_PLAY:
			manager.getPlayer().togglePlay();
			break;
		case RESUME:
			manager.getPlayer().resume();
			break;
		case STOP:
			manager.getPlayer().stop();
			break;
		case NEXT_COMPOSITION:
			manager.getCurrentSet().nextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			manager.getCurrentSet().previousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			manager.getPlayer().setCompositionName(midiControl.getSelectComposition());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			manager.getPlayer().setCompositionName(midiControl.getSelectComposition());
			manager.getPlayer().play();
			break;
		case REBOOT:
			manager.reboot();
			break;
		default:
			logger.warn("Action '" + midiControl.getAction().toString() + "' is locally unknown and cannot be executed");
			break;
		}
	}

	/**
	 * Execute the action according to the actionMapping-element.
	 * 
	 * @param actionMapping
	 * @throws Exception
	 */
	private void executeActionMappingAction(MidiControl midiControl) throws Exception {
		if (midiControl.isExecuteLocally()) {
			executeActionLocally(midiControl);
		}

		// Execute the action on each specified remote device
		for (String name : midiControl.getRemoteDeviceNames()) {
			RemoteDevice remoteDevice = manager.getSettings().getRemoteDeviceByName(name);

			if (remoteDevice == null) {
				logger.warn("No remote device could be found in the settings with name " + name);
			} else {
				executeActionOnRemoteDevice(midiControl, remoteDevice);
			}
		}
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
				executeActionMappingAction(midiControl);
			}
		}
	}

}
