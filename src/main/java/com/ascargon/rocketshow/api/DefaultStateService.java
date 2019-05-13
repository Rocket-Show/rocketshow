package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import org.springframework.stereotype.Service;

@Service
public class DefaultStateService implements StateService {

    private int getCompositionIndexWithoutSet(CompositionService compositionService, String compositionName) {
        return compositionService.getCompositionIndex(compositionName);
    }

    @Override
    public State getCurrentState(PlayerService playerService, SetService setService, CompositionService compositionService) {
        State currentState = new State();

        currentState.setPlayState(CompositionPlayer.PlayState.STOPPED);
        currentState.setCurrentCompositionIndex(0);

        if (playerService != null) {
            currentState.setPlayState(playerService.getPlayState());
            currentState.setCurrentCompositionName(playerService.getCompositionName());
            currentState.setCurrentCompositionDurationMillis(playerService.getCompositionDurationMillis());
            currentState.setPositionMillis(playerService.getPositionMillis());
        }

        if (setService == null) {
            if (playerService != null) {
                currentState.setCurrentCompositionIndex(getCompositionIndexWithoutSet(compositionService, playerService.getCompositionName()));
            }
        } else {
            currentState.setCurrentCompositionIndex(setService.getCurrentCompositionIndex());

            if (setService.getCurrentSet() == null && playerService != null) {
                currentState.setCurrentCompositionIndex(getCompositionIndexWithoutSet(compositionService, playerService.getCompositionName()));
            } else {
                currentState.setCurrentSetName(setService.getCurrentSet().getName());
            }
        }

        return currentState;
    }

}
