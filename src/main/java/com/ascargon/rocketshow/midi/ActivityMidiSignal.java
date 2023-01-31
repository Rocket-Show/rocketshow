package com.ascargon.rocketshow.midi;

import javax.sound.midi.ShortMessage;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
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

    public int getCommand() {
        return command;
    }

    public void setCommand(int command) {
        this.command = command;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getNote() {
        return note;
    }

    public void setNote(int note) {
        this.note = note;
    }

    public int getVelocity() {
        return velocity;
    }

    public void setVelocity(int velocity) {
        this.velocity = velocity;
    }

}
