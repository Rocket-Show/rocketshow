package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.UpdateService;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class State {

    private int currentCompositionIndex;
    private CompositionPlayer.PlayState playState;
    private String currentCompositionName;
    private long currentCompositionDurationMillis;
    private long positionMillis;
    private MidiSignal midiSignal;
    private UpdateService.UpdateState updateState;
    private String currentSetName;
    private Boolean updateFinished;

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

    public MidiSignal getMidiSignal() {
        return midiSignal;
    }

    public void setMidiSignal(MidiSignal midiSignal) {
        this.midiSignal = midiSignal;
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

}
