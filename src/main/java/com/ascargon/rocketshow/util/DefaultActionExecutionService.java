package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.lighting.LightingAction;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.midi.*;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioAction;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultActionExecutionService implements ActionExecutionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActionExecutionService.class);

    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final RebootService rebootService;
    private final LightingService lightingService;
    private final MidiRouter midiRouter;
    private final RaspberryGpioOutService raspberryGpioOutService;

    public DefaultActionExecutionService(PlayerService playerService, SettingsService settingsService, RebootService rebootService, LightingService lightingService, MidiRouterFactory midiRouterFactory, RaspberryGpioOutService raspberryGpioOutService) {
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.rebootService = rebootService;
        this.lightingService = lightingService;
        this.raspberryGpioOutService = raspberryGpioOutService;

        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getRemoteMidiRoutingList());
    }

    private void executeActionOnRemoteDevice(Action action, RemoteDevice remoteDevice) {
        remoteDevice.doPost("execute-action", action);
    }

    private void executeActionLocally(Action action) throws Exception {
        switch (action.getActionType()) {
            case TRANSPORT -> {
                TransportAction transportAction = (TransportAction) action;
                switch (transportAction.getTransportActionType()) {
                    case PLAY -> playerService.play();
                    case PLAY_AS_SAMPLE -> playerService.playAsSample(transportAction.getCompositionName());
                    case TOGGLE_PLAY -> playerService.togglePlay();
                    case PAUSE -> playerService.pause();
                    case NEXT_COMPOSITION -> playerService.setNextComposition();
                    case PREVIOUS_COMPOSITION -> playerService.setPreviousComposition();
                    case STOP -> playerService.stop();
                    case SELECT_COMPOSITION_BY_NAME ->
                            playerService.setCompositionName(transportAction.getCompositionName());
                    case SELECT_COMPOSITION_BY_NAME_AND_PLAY -> {
                        playerService.setCompositionName(transportAction.getCompositionName());
                        playerService.play();
                    }
                }
            }
            case MIDI ->
                    midiRouter.sendSignal(((MidiAction) action).getMidiSignal().getShortMessage(), MidiSource.ACTION);
            case LIGHTING -> lightingService.executeAction((LightingAction) action);
            case RASPBERRY_GPIO -> raspberryGpioOutService.executeAction((RaspberryGpioAction) action);
            case SYSTEM -> {
                SystemAction systemAction = (SystemAction) action;
                switch (systemAction.getSystemActionType()) {
                    case REBOOT -> rebootService.reboot();
                }
            }
            default -> logger.warn("Action '" + action.getActionType() + "' is unknown and cannot be executed");
        }
    }

    @Override
    public void execute(Action action) throws Exception {
        if (action.isExecuteLocally()) {
            executeActionLocally(action);
        }

        // Execute the action on each specified remote device
        for (String remoteDeviceName : action.getRemoteDeviceNames()) {
            RemoteDevice remoteDevice = settingsService.getRemoteDeviceByName(remoteDeviceName);

            if (remoteDevice == null) {
                logger.warn("No remote device could be found in the settings with name " + remoteDeviceName);
            } else {
                executeActionOnRemoteDevice(action, remoteDevice);
            }
        }
    }

    /**
     * Execute the control action.
     */
    @Override
    public void executeFromTrigger(ActionTrigger actionTrigger) throws Exception {
        for (Action action : actionTrigger.getActionList()) {
            execute(action);
        }
    }

}
