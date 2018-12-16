package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.composition.CompositionFile;

import javax.xml.bind.annotation.XmlElement;

public class AudioCompositionFile extends CompositionFile {

    private String outputBus;
    private int channels = 2;

    public String getOutputBus() {
        return outputBus;
    }

    public void setOutputBus(String outputBus) {
        this.outputBus = outputBus;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public CompositionFileType getType() {
        return CompositionFileType.AUDIO;
    }

}
