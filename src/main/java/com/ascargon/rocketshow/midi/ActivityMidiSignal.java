package com.ascargon.rocketshow.midi;

import javax.sound.midi.ShortMessage;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class ActivityMidiSignal {

    private int command;
    private int channel;
    private int note;
    private int velocity;

    public ActivityMidiSignal() {
    }

    public ActivityMidiSignal(ShortMessage shortMessage) {
        command = shortMessage.getCommand();
        channel = shortMessage.getChannel();
        note = shortMessage.getData1();
        velocity = shortMessage.getData2();
    }

}
