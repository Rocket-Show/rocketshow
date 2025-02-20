package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
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

    public ActivityMidiSignal(int command, int channel, int note, int velocity) {
        this.command = command;
        this.channel = channel;
        this.note = note;
        this.velocity = velocity;
    }

    public ActivityMidiSignal(ShortMessage shortMessage) {
        command = shortMessage.getCommand();
        channel = shortMessage.getChannel();
        note = shortMessage.getData1();
        velocity = shortMessage.getData2();
    }

    public ShortMessage getShortMessage() throws InvalidMidiDataException {
        ShortMessage shortMessage = new ShortMessage();
        shortMessage.setMessage(command, channel, note, velocity);
        return shortMessage;
    }

}
