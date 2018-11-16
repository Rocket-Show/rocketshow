package com.ascargon.rocketshow.util;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.RemoteDevice;

public class ControlActionExecuter {

	private final static Logger logger = Logger.getLogger(ControlActionExecuter.class);

	private Manager manager;

	public ControlActionExecuter(Manager manager) {
		this.manager = manager;
	}

	private void executeActionOnRemoteDevice(ControlAction controlAction, RemoteDevice remoteDevice) {
		switch (controlAction.getAction()) {
		case PLAY:
			remoteDevice.play();
			break;
		case PLAY_AS_SAMPLE:
			remoteDevice.playAsSample(controlAction.getCompositionName());
			break;
		case PAUSE:
			remoteDevice.pause();
			break;
		case TOGGLE_PLAY:
			remoteDevice.togglePlay();
			break;
		case STOP:
			remoteDevice.stop(true);
			break;
		case NEXT_COMPOSITION:
			remoteDevice.setNextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			remoteDevice.setPreviousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			remoteDevice.setCompositionName(controlAction.getCompositionName());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			remoteDevice.setCompositionName(controlAction.getCompositionName());
			remoteDevice.play();
			break;
		case SET_COMPOSITION_INDEX:
			remoteDevice.setCompositionIndex(manager.getCurrentCompositionSet().getCurrentCompositionIndex());
			break;
		case REBOOT:
			remoteDevice.reboot();
			break;
		default:
			logger.warn("Action '" + controlAction.getAction().toString()
					+ "' is unknown for remote devices and cannot be executed");
			break;
		}
	}

	private void executeActionLocally(ControlAction controlAction) throws Exception {
		// Execute the action locally
		logger.info("Execute action from MIDI event");

		switch (controlAction.getAction()) {
		case PLAY:
			manager.getPlayer().play();
			break;
		case PLAY_AS_SAMPLE:
			manager.getPlayer().playAsSample(controlAction.getCompositionName());
			break;
		case PAUSE:
			manager.getPlayer().pause();
			break;
		case TOGGLE_PLAY:
			manager.getPlayer().togglePlay();
			break;
		case STOP:
			manager.getPlayer().stop();
			break;
		case NEXT_COMPOSITION:
			manager.getCurrentCompositionSet().nextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			manager.getCurrentCompositionSet().previousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			manager.getPlayer().setCompositionName(controlAction.getCompositionName());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			manager.getPlayer().setCompositionName(controlAction.getCompositionName());
			manager.getPlayer().play();
			break;
		case REBOOT:
			manager.reboot();
			break;
		default:
			logger.warn(
					"Action '" + controlAction.getAction().toString() + "' is locally unknown and cannot be executed");
			break;
		}
	}

	/**
	 * Execute the control action.
	 */
	public void execute(ControlAction controlAction) throws Exception {
		if (controlAction.isExecuteLocally()) {
			executeActionLocally(controlAction);
		}

		// Execute the action on each specified remote device
		for (String name : controlAction.getRemoteDeviceNames()) {
			RemoteDevice remoteDevice = manager.getSettings().getRemoteDeviceByName(name);

			if (remoteDevice == null) {
				logger.warn("No remote device could be found in the settings with name " + name);
			} else {
				executeActionOnRemoteDevice(controlAction, remoteDevice);
			}
		}
	}

}
