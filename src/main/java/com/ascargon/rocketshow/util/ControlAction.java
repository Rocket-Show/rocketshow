package com.ascargon.rocketshow.util;

import java.util.ArrayList;
import java.util.List;

import com.ascargon.rocketshow.lighting.LightingAction;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.raspberry.RaspberryGpioAction;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class ControlAction {

    // Actions to be executed (e.g. by MIDI control or Raspberry GPIO events)
    public enum Action {
        PLAY,
        PLAY_AS_SAMPLE,
        TOGGLE_PLAY,
        PAUSE,
        NEXT_COMPOSITION,
        PREVIOUS_COMPOSITION,
        STOP,
        REBOOT,
        SELECT_COMPOSITION_BY_NAME,
        SELECT_COMPOSITION_BY_NAME_AND_PLAY,
        MIDI,
        LIGHTING,
        GPIO
    }

    // The Action to be executed
    private Action action;

    // Used for:
    // - SELECT_COMPOSITION_BY_NAME
    // - SELECT_COMPOSITION_BY_NAME_AND_PLAY
    private String compositionName;

    // Used for MIDI
    private MidiSignal midiSignal;

    // Used for LIGHTING
    private LightingAction lightingAction;

    // Used for GPIO
    private RaspberryGpioAction raspberryGpioAction;

    // Execute this action on remote devices
    private List<String> remoteDeviceNames = new ArrayList<>();

    // Execute this action locally?
    private boolean executeLocally = true;

    @XmlElement(name = "remoteDevice")
    @XmlElementWrapper(name = "remoteDeviceList")
    @SuppressWarnings("WeakerAccess")
    public List<String> getRemoteDeviceNames() {
        return remoteDeviceNames;
    }

    @SuppressWarnings("unused")
    public void setRemoteDeviceNames(List<String> remoteDeviceNames) {
        this.remoteDeviceNames = remoteDeviceNames;
    }

}
