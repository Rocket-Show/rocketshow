package com.ascargon.rocketshow.audio;

import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.composition.CompositionFile;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

public class AudioCompositionFile extends CompositionFile {

    private String outputBus;

    public String getOutputBus() {
        return outputBus;
    }

    public void setOutputBus(String outputBus) {
        this.outputBus = outputBus;
    }

}
