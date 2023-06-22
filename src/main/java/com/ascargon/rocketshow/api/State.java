package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.util.UpdateService;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class State {

    private int currentCompositionIndex;
    private CompositionPlayer.PlayState playState;
    private String currentCompositionName;
    private long currentCompositionDurationMillis;
    private long positionMillis;
    private UpdateService.UpdateState updateState;
    private String currentSetName;
    private Boolean updateFinished;
    private String error;

    public int getCurrentCompositionIndex() {
        return currentCompositionIndex;
    }

    public void setCurrentCompositionIndex(int currentCompositionIndex) {
        this.currentCompositionIndex = currentCompositionIndex;
    }

    public CompositionPlayer.PlayState getPlayState() {
        return playState;
    }

    public void setPlayState(CompositionPlayer.PlayState playState) {
        this.playState = playState;
    }

    public String getCurrentCompositionName() {
        return currentCompositionName;
    }

    public void setCurrentCompositionName(String currentCompositionName) {
        this.currentCompositionName = currentCompositionName;
    }

    public long getCurrentCompositionDurationMillis() {
        return currentCompositionDurationMillis;
    }

    public void setCurrentCompositionDurationMillis(long currentCompositionDurationMillis) {
        this.currentCompositionDurationMillis = currentCompositionDurationMillis;
    }

    public long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public UpdateService.UpdateState getUpdateState() {
        return updateState;
    }

    public void setUpdateState(UpdateService.UpdateState updateState) {
        this.updateState = updateState;
    }

    public String getCurrentSetName() {
        return currentSetName;
    }

    public void setCurrentSetName(String currentSetName) {
        this.currentSetName = currentSetName;
    }

    public Boolean isUpdateFinished() {
        return updateFinished;
    }

    public void setUpdateFinished(Boolean updateFinished) {
        this.updateFinished = updateFinished;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

}
