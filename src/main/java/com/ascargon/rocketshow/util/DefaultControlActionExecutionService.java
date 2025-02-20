package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultControlActionExecutionService implements ControlActionExecutionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultControlActionExecutionService.class);

    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final RebootService rebootService;
    private final LightingService lightingService;

    public DefaultControlActionExecutionService(PlayerService playerService, SettingsService settingsService, RebootService rebootService, LightingService lightingService) {
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.rebootService = rebootService;
        this.lightingService = lightingService;
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
            case LIGHTING:
                remoteDevice.executeLightingAction(controlAction.getLightingAction());
                break;
            case REBOOT:
                remoteDevice.reboot();
                break;
            default:
                logger.warn("Action '" + controlAction.getAction()
                        + "' is unknown for remote devices and cannot be executed");
                break;
        }
    }

    private void executeActionLocally(ControlAction controlAction) throws Exception {
        // Execute the action locally
        logger.info("Execute action from control event");

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
                playerService.setNextComposition();
                break;
            case PREVIOUS_COMPOSITION:
                playerService.setPreviousComposition();
                break;
            case SELECT_COMPOSITION_BY_NAME:
                playerService.setCompositionName(controlAction.getCompositionName());
                break;
            case SELECT_COMPOSITION_BY_NAME_AND_PLAY:
                playerService.setCompositionName(controlAction.getCompositionName());
                playerService.play();
                break;
            case LIGHTING:
                lightingService.executeAction(controlAction.getLightingAction());
                break;
            case REBOOT:
                rebootService.reboot();
                break;
            default:
                logger.warn(
                        "Action '" + controlAction.getAction() + "' is locally unknown and cannot be executed");
                break;
        }
    }

    /**
     * Execute the control action.
     */
    @Override
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
