package com.ascargon.rocketshow.api;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ActivityAudioBus {

    private String name;

    private List<ActivityAudioChannel> activityAudioChannelList = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ActivityAudioChannel> getActivityAudioChannelList() {
        return activityAudioChannelList;
    }

    public void setActivityAudioChannelList(List<ActivityAudioChannel> activityAudioChannelList) {
        this.activityAudioChannelList = activityAudioChannelList;
    }

}
