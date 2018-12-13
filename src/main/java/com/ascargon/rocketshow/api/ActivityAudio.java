package com.ascargon.rocketshow.api;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ActivityAudio {

    private List<ActivityAudioBus> activityAudioBusList = new ArrayList<>();

    public List<ActivityAudioBus> getActivityAudioBusList() {
        return activityAudioBusList;
    }

    public void setActivityAudioBusList(List<ActivityAudioBus> activityAudioBusList) {
        this.activityAudioBusList = activityAudioBusList;
    }

}
