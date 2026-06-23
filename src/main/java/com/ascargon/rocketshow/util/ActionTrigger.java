package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.ActionHttp;
import com.ascargon.rocketshow.lighting.ActionLighting;
import com.ascargon.rocketshow.midi.ActionMidi;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class ActionTrigger {

    private List<Action> actionList = new ArrayList<>();

    @XmlElementWrapper(name = "actionList")
    @XmlElements({@XmlElement(type = ActionNull.class, name = "actionNull"),
            @XmlElement(type = ActionSystem.class, name = "actionSystem"),
            @XmlElement(type = ActionTransport.class, name = "actionTransport"),
            @XmlElement(type = ActionMidi.class, name = "actionMidi"),
            @XmlElement(type = ActionLighting.class, name = "actionLighting"),
            @XmlElement(type = ActionRaspberryGpio.class, name = "actionRaspberryGpio"),
            @XmlElement(type = ActionHttp.class, name = "actionHttp")})
    @JsonProperty("actionList")
    public List<Action> getActionList() {
        return actionList;
    }

}
