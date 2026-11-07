package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import com.ascargon.rocketshow.lighting.designer.TempoService;
import com.ascargon.rocketshow.midi.MidiTimecodeSlaveService;
import com.ascargon.rocketshow.play.PlayerService;
import org.springframework.stereotype.Service;

@Service
public class DefaultStateService implements StateService {

    private final ScenePlayerService scenePlayerService;
    private final MidiTimecodeSlaveService midiTimecodeSlaveService;
    private final TempoService tempoService;

    public DefaultStateService(ScenePlayerService scenePlayerService, MidiTimecodeSlaveService midiTimecodeSlaveService, TempoService tempoService) {
        this.scenePlayerService = scenePlayerService;
        this.midiTimecodeSlaveService = midiTimecodeSlaveService;
        this.tempoService = tempoService;
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

        currentState.setMidiTimecodeLocked(midiTimecodeSlaveService.isLocked());

        long midiTimecodeMillis = midiTimecodeSlaveService.getTimecodeMillis();

        if (midiTimecodeMillis >= 0) {
            currentState.setMidiTimecodeMillis(midiTimecodeMillis);
        }

        if (playerService != null) {
            currentState.setPlayState(playerService.getPlayState());
            currentState.setCurrentCompositionName(playerService.getCompositionName());
            currentState.setCurrentCompositionDurationMillis(playerService.getCompositionDurationMillis());
            currentState.setPositionMillis(playerService.getPositionMillis());
            currentState.setHolding(playerService.isHolding());
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
        currentState.setFlashedSceneUuids(scenePlayerService.getFlashedSceneUuids());
        currentState.setSceneSpeed(scenePlayerService.getSpeed());
        currentState.setSceneDimmers(scenePlayerService.getSceneDimmers());
        currentState.setSceneLearning(scenePlayerService.getLearning());

        currentState.setTempoBeatsPerMinute(tempoService.getBeatsPerMinute());
        currentState.setTempoSource(tempoService.getSource());
        currentState.setTempoBeats(tempoService.getBeats());
        currentState.setTempoBeatsPerBar(tempoService.getBeatsPerBar());

        return currentState;
    }

}
