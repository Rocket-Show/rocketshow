package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class MidiSignal {

    private int command;
    private int channel;
    private int data1;
    private int data2;

    public MidiSignal() {
    }

    public MidiSignal(int command, int channel, int data1, int data2) {
        this.command = command;
        this.channel = channel;
        this.data1 = data1;
        this.data2 = data2;
    }

    public MidiSignal(ShortMessage shortMessage) {
        command = shortMessage.getCommand();
        channel = shortMessage.getChannel();
        data1 = shortMessage.getData1();
        data2 = shortMessage.getData2();
    }

    @JsonIgnore
    public ShortMessage getShortMessage() throws InvalidMidiDataException {
        ShortMessage shortMessage = new ShortMessage();
        shortMessage.setMessage(command, channel, data1, data2);
        return shortMessage;
    }

}
