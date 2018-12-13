package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ActivityMidi {

    private MidiSignal midiSignal;

    private MidiSignal.MidiDirection midiDirection;

    private MidiSignal.MidiSource midiSource;

    private MidiSignal.MidiDestination midiDestination;

    public MidiSignal getMidiSignal() {
        return midiSignal;
    }

    public void setMidiSignal(MidiSignal midiSignal) {
        this.midiSignal = midiSignal;
    }

    public MidiSignal.MidiDirection getMidiDirection() {
        return midiDirection;
    }

    public void setMidiDirection(MidiSignal.MidiDirection midiDirection) {
        this.midiDirection = midiDirection;
    }

    public MidiSignal.MidiDestination getMidiDestination() {
        return midiDestination;
    }

    public void setMidiDestination(MidiSignal.MidiDestination midiDestination) {
        this.midiDestination = midiDestination;
    }

    public MidiSignal.MidiSource getMidiSource() {
        return midiSource;
    }

    public void setMidiSource(MidiSignal.MidiSource midiSource) {
        this.midiSource = midiSource;
    }

}
