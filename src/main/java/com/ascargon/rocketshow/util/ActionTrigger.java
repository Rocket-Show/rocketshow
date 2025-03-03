package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.api.HttpAction;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.lighting.LightingAction;
import com.ascargon.rocketshow.midi.MidiAction;
import com.ascargon.rocketshow.raspberry.RaspberryGpioAction;
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
    @XmlElements({@XmlElement(type = NullAction.class, name = "nullAction"),
            @XmlElement(type = SystemAction.class, name = "systemAction"),
            @XmlElement(type = TransportAction.class, name = "transportAction"),
            @XmlElement(type = MidiAction.class, name = "midiAction"),
            @XmlElement(type = LightingAction.class, name = "lightingAction"),
            @XmlElement(type = RaspberryGpioAction.class, name = "raspberryGpioAction"),
            @XmlElement(type = HttpAction.class, name = "httpAction")})
    @JsonProperty("actionList")
    public List<Action> getActionList() {
        return actionList;
    }

}
