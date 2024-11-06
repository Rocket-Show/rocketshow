package com.ascargon.rocketshow.audio;

import com.ascargon.rocketshow.composition.CompositionFile;

import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AudioCompositionFile extends CompositionFile {

    // the name of the audio bus
    private String outputBus;
    private int channels = 2;
    private float volume = 1;

    public CompositionFileType getType() {
        return CompositionFileType.AUDIO;
    }

}
