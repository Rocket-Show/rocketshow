package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import com.ascargon.rocketshow.play.PlayerService;
import org.springframework.stereotype.Service;

@Service
public class DefaultStateService implements StateService {

    private final ScenePlayerService scenePlayerService;

    public DefaultStateService(ScenePlayerService scenePlayerService) {
        this.scenePlayerService = scenePlayerService;
    }

    private int getCompositionIndexWithoutSet(
            CompositionService compositionService,
            String compositionName
    ) {
        return compositionService.getCompositionIndex(compositionName);
    }

    @Override
    public State getCurrentState(
            PlayerService playerService,
            SetService setService,
            CompositionService compositionService
    ) {
        State currentState = new State();

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

            if (setService.getCurrentSet() != null) {
                currentState.setCurrentSetName(setService.getCurrentSet().getName());
            } else if (playerService != null) {
                currentState.setCurrentCompositionIndex(getCompositionIndexWithoutSet(compositionService, playerService.getCompositionName()));
            }
        }

        currentState.setSceneProjectName(scenePlayerService.getProjectName());
        currentState.setActiveSceneUuids(scenePlayerService.getActiveSceneUuids());
        currentState.setSceneBlackout(scenePlayerService.isBlackout());
        currentState.setSceneMasterDimmer(scenePlayerService.getMasterDimmer());
        currentState.setScenesHeld(scenePlayerService.isHeld());

        return currentState;
    }

}
