package com.ascargon.rocketshow.audio;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * An audio bus containing a number of channels.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
public class AudioBus {

    private String uuid = String.valueOf(UUID.randomUUID());
    private AudioDevice audioDevice;
    private String name;
    private int channels = 2;

}
