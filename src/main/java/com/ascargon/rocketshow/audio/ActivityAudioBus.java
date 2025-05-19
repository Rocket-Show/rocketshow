package com.ascargon.rocketshow.audio;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Getter
@Setter
public class ActivityAudioBus {

    private String name;
    private List<ActivityAudioChannel> activityAudioChannelList = new ArrayList<>();

}
