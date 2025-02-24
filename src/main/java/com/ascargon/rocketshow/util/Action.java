package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.HttpAction;
import com.ascargon.rocketshow.lighting.LightingAction;
import com.ascargon.rocketshow.midi.MidiAction;
import com.ascargon.rocketshow.raspberry.RaspberryGpioAction;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SystemAction.class, name = "systemAction"),
        @JsonSubTypes.Type(value = TransportAction.class, name = "transportAction"),
        @JsonSubTypes.Type(value = MidiAction.class, name = "midiAction"),
        @JsonSubTypes.Type(value = LightingAction.class, name = "lightingAction"),
        @JsonSubTypes.Type(value = RaspberryGpioAction.class, name = "raspberryGpioAction"),
        @JsonSubTypes.Type(value = HttpAction.class, name = "httpAction")
})
@Getter
@Setter
public abstract class Action {

    // Actions to be executed (e.g. by MIDI control or Raspberry GPIO events)
    public enum ActionType {
        SYSTEM,
        TRANSPORT,
        MIDI,
        LIGHTING,
        RASPBERRY_GPIO,
        HTTP
    }

    // The Action to be executed
    private ActionType actionType;

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

    public abstract ActionType getType();

}
