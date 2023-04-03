package com.ascargon.rocketshow.api;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ActivityAudioChannel {

    private int index = 0;

    private double volumeDb = 0;

    public int getIndex() {
        return index;
    }

    public void setIndex(int channel) {
        this.index = channel;
    }

    public double getVolumeDb() {
        return volumeDb;
    }

    public void setVolumeDb(double volumeDb) {
        this.volumeDb = volumeDb;
    }

}
