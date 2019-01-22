package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.MidiSignal;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ActivityMidi {

    private MidiSignal midiSignal;

    private MidiSignal.MidiDirection midiDirection;

    private List<MidiSignal.MidiSource> midiSources = new ArrayList<>();

    private List<MidiSignal.MidiDestination> midiDestinations = new ArrayList<>();

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

    public List<MidiSignal.MidiSource> getMidiSources() {
        return midiSources;
    }

    public void setMidiSources(List<MidiSignal.MidiSource> midiSources) {
        this.midiSources = midiSources;
    }

    public List<MidiSignal.MidiDestination> getMidiDestinations() {
        return midiDestinations;
    }

    public void setMidiDestinations(List<MidiSignal.MidiDestination> midiDestinations) {
        this.midiDestinations = midiDestinations;
    }
}
