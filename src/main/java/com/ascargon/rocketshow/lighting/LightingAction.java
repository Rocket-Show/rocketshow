package com.ascargon.rocketshow.lighting;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Getter
@Setter
public class LightingAction {

    private List<LightingActionUniverse> lightingActionUniverseList = new ArrayList<>();

}
