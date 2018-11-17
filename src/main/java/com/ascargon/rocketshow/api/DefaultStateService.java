package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SessionService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.composition.SetService;
import org.springframework.stereotype.Service;

@Service
public class DefaultStateService implements StateService {

    private PlayerService playerService;
    private SetService setService;
    private SessionService sessionService;

    public DefaultStateService(PlayerService playerService, SetService setService, SessionService sessionService) {
        this.playerService = playerService;
        this.setService = setService;
        this.sessionService = sessionService;
    }

    public State getCurrentState() {
        State currentState = new State();

        currentState.setPlayState(CompositionPlayer.PlayState.STOPPED);
        currentState.setCurrentCompositionIndex(0);

        currentState.setPlayState(playerService.getPlayState());
        currentState.setCurrentCompositionName(playerService.getCompositionName());
        currentState.setCurrentCompositionDurationMillis(playerService.getCompositionDurationMillis());
        currentState.setPositionMillis(playerService.getPositionMillis());

        currentState.setCurrentCompositionIndex(setService.getCurrentCompositionIndex());

        if(setService.getCurrentSet() != null) {
            currentState.setCurrentSetName(setService.getCurrentSet().getName());
        }

        currentState.setUpdateFinished(sessionService.getSession().isUpdateFinished());

        return currentState;
    }

}
