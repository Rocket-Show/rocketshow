package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.composition.SetService;
import org.springframework.stereotype.Service;

@Service
public class DefaultStateService implements StateService {

    @Override
    public State getCurrentState(PlayerService playerService, SetService setService) {
        State currentState = new State();

        currentState.setPlayState(CompositionPlayer.PlayState.STOPPED);
        currentState.setCurrentCompositionIndex(0);

        if (playerService != null) {
            currentState.setPlayState(playerService.getPlayState());
            currentState.setCurrentCompositionName(playerService.getCompositionName());
            currentState.setCurrentCompositionDurationMillis(playerService.getCompositionDurationMillis());
            currentState.setPositionMillis(playerService.getPositionMillis());
        }

        if (setService != null) {
            currentState.setCurrentCompositionIndex(setService.getCurrentCompositionIndex());

            if (setService.getCurrentSet() != null) {
                currentState.setCurrentSetName(setService.getCurrentSet().getName());
            }
        }

        return currentState;
    }

}
