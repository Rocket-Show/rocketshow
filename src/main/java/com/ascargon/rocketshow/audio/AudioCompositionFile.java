package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.composition.CompositionFile;

public class AudioCompositionFile extends CompositionFile {

    private String outputBus;

    public String getOutputBus() {
        return outputBus;
    }

    public void setOutputBus(String outputBus) {
        this.outputBus = outputBus;
    }

}
