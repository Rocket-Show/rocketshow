package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.ActionHttp;
import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.lighting.ActionLighting;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.midi.ActionMidi;
import com.ascargon.rocketshow.midi.MidiRouter;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.midi.MidiSource;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DefaultActionExecutionService implements ActionExecutionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultActionExecutionService.class);

    private final PlayerService playerService;
    private final SettingsService settingsService;
    private final RebootService rebootService;
    private final LightingService lightingService;
    private final MidiRouter midiRouter;
    private final RaspberryGpioOutService raspberryGpioOutService;

    private final HttpClient httpClient;

    // Lazy load the playerService to avoid a circular dependency, because the playerService can execute actions and
    // is called by this service for transport actions.
    public DefaultActionExecutionService(@Lazy PlayerService playerService, SettingsService settingsService, RebootService rebootService, LightingService lightingService, MidiRouterFactory midiRouterFactory, RaspberryGpioOutService raspberryGpioOutService) {
        this.playerService = playerService;
        this.settingsService = settingsService;
        this.rebootService = rebootService;
        this.lightingService = lightingService;
        this.raspberryGpioOutService = raspberryGpioOutService;

        midiRouter = midiRouterFactory.getMidiRouter(settingsService.getSettings().getRemoteMidiRoutingList());

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(60000 /* 60 seconds */).build();
        httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    }

    private void executeActionHttp(ActionHttp actionHttp) throws Exception {
        logger.debug("Execute HTTP action to URL " + actionHttp.getUrl());

        HttpRequestBase request;

        switch (actionHttp.getHttpMethod()) {
            case POST:
                HttpPost postRequest = new HttpPost(actionHttp.getUrl());
                if (actionHttp.getBody() != null) {
                    postRequest.setEntity(new StringEntity(actionHttp.getBody()));
                }
                request = postRequest;
                break;
            case PUT:
                HttpPut putRequest = new HttpPut(actionHttp.getUrl());
                if (actionHttp.getBody() != null) {
                    putRequest.setEntity(new StringEntity(actionHttp.getBody()));
                }
                request = putRequest;
                break;
            case DELETE:
                request = new HttpDelete(actionHttp.getUrl());
                break;
            case PATCH:
                HttpPatch patchRequest = new HttpPatch(actionHttp.getUrl());
                if (actionHttp.getBody() != null) {
                    patchRequest.setEntity(new StringEntity(actionHttp.getBody()));
                }
                request = patchRequest;
                break;
            case GET:
            default:
                request = new HttpGet(actionHttp.getUrl());
                break;
        }

        for (Map.Entry<String, String> header : actionHttp.getHeaderList().entrySet()) {
            request.setHeader(header.getKey(), header.getValue());
        }

        HttpResponse response;
        response = httpClient.execute(request);

        logger.debug("Received response from HTTP action");
        logger.debug("Status code: " + response.getStatusLine().getStatusCode());
        logger.debug("Status phrase: " + response.getStatusLine().getReasonPhrase());
        logger.debug("Response body: " + EntityUtils.toString(response.getEntity()));
    }

    private void executeActionOnRemoteDevice(Action action, RemoteDevice remoteDevice) {
        remoteDevice.doPost("execute-action", action);
    }

    private void executeActionLocally(Action action) throws Exception {
        switch (action.getType()) {
            case TRANSPORT -> {
                ActionTransport actionTransport = (ActionTransport) action;
                switch (actionTransport.getTransportActionType()) {
                    case PLAY -> playerService.play();
                    case PLAY_AS_SAMPLE -> playerService.playAsSample(actionTransport.getCompositionName());
                    case TOGGLE_PLAY -> playerService.togglePlay();
                    case PAUSE -> playerService.pause();
                    case NEXT_COMPOSITION -> playerService.setNextComposition();
                    case PREVIOUS_COMPOSITION -> playerService.setPreviousComposition();
                    case STOP -> playerService.stop();
                    case SELECT_COMPOSITION_BY_NAME ->
                            playerService.setCompositionName(actionTransport.getCompositionName());
                    case SELECT_COMPOSITION_BY_NAME_AND_PLAY -> {
                        playerService.setCompositionName(actionTransport.getCompositionName());
                        playerService.play();
                    }
                }
            }
            case MIDI ->
                    midiRouter.sendSignal(((ActionMidi) action).getMidiSignal().getShortMessage(), MidiSource.ACTION);
            case LIGHTING -> lightingService.executeAction((ActionLighting) action);
            case RASPBERRY_GPIO -> raspberryGpioOutService.executeAction((ActionRaspberryGpio) action);
            case SYSTEM -> {
                ActionSystem actionSystem = (ActionSystem) action;
                switch (actionSystem.getSystemActionType()) {
                    case REBOOT -> rebootService.reboot();
                }
            }
            case HTTP -> executeActionHttp((ActionHttp) action);
            default -> logger.warn("Action '" + action.getType() + "' is unknown and cannot be executed");
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
