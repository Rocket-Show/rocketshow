package com.ascargon.rocketshow.audio;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class ActivityAudioChannel {

    private int index = 0;
    private double volumeDb = 0;

}
