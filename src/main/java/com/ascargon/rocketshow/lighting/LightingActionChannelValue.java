package com.ascargon.rocketshow.lighting;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LightingActionChannelValue {

    private Integer channel;
    private Integer value;

}
