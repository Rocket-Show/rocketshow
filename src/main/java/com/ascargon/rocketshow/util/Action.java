package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.ActionHttp;
import com.ascargon.rocketshow.lighting.ActionLighting;
import com.ascargon.rocketshow.midi.ActionMidi;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
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
        @JsonSubTypes.Type(value = ActionNull.class, name = "actionNull"),
        @JsonSubTypes.Type(value = ActionSystem.class, name = "actionSystem"),
        @JsonSubTypes.Type(value = ActionTransport.class, name = "actionTransport"),
        @JsonSubTypes.Type(value = ActionMidi.class, name = "actionMidi"),
        @JsonSubTypes.Type(value = ActionLighting.class, name = "actionLighting"),
        @JsonSubTypes.Type(value = ActionRaspberryGpio.class, name = "actionRaspberryGpio"),
        @JsonSubTypes.Type(value = ActionHttp.class, name = "actionHttp")
})
public abstract class Action {

    // Actions to be executed (e.g. by MIDI control or Raspberry GPIO events)
    public enum ActionType {
        NULL,
        SYSTEM,
        TRANSPORT,
        MIDI,
        LIGHTING,
        RASPBERRY_GPIO,
        HTTP
    }

    // Execute this action on remote devices
    private List<String> remoteDeviceNames = new ArrayList<>();

    // Execute this action locally?
    @Getter
    @Setter
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
