package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.*;
import com.ascargon.rocketshow.composition.SetService;
import org.apache.log4j.Logger;

public class ControlActionExecuter {

	private final static Logger logger = Logger.getLogger(ControlActionExecuter.class);

	private PlayerService playerService;
    private SetService setService;
    private SettingsService settingsService;

	private Manager manager;

	public ControlActionExecuter(PlayerService playerService, SetService setService, SettingsService settingsService, Manager manager) {
		this.playerService = playerService;
		this.setService = setService;
		this.settingsService = settingsService;

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
			remoteDevice.setCompositionIndex(setService.getCurrentCompositionIndex());
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
			playerService.play();
			break;
		case PLAY_AS_SAMPLE:
            playerService.playAsSample(controlAction.getCompositionName());
			break;
		case PAUSE:
            playerService.pause();
			break;
		case TOGGLE_PLAY:
            playerService.togglePlay();
			break;
		case STOP:
            playerService.stop();
			break;
		case NEXT_COMPOSITION:
			setService.nextComposition();
			break;
		case PREVIOUS_COMPOSITION:
			setService.previousComposition();
			break;
		case SELECT_COMPOSITION_BY_NAME:
			playerService.setCompositionName(controlAction.getCompositionName());
			break;
		case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
			playerService.setCompositionName(controlAction.getCompositionName());
			playerService.play();
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
			RemoteDevice remoteDevice = settingsService.getRemoteDeviceByName(name);

			if (remoteDevice == null) {
				logger.warn("No remote device could be found in the settings with name " + name);
			} else {
				executeActionOnRemoteDevice(controlAction, remoteDevice);
			}
		}
	}

}
