package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.midi.ActivityMidiSignal;
import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class ActivityMidi {

    private ActivityMidiSignal midiSignal;

    private MidiDirection midiDirection;

    private List<MidiSource> midiSources = new ArrayList<>();

    private List<MidiDestination> midiDestinations = new ArrayList<>();

    public ActivityMidiSignal getMidiSignal() {
        return midiSignal;
    }

    public void setMidiSignal(ActivityMidiSignal midiSignal) {
        this.midiSignal = midiSignal;
    }

    public MidiDirection getMidiDirection() {
        return midiDirection;
    }

    public void setMidiDirection(MidiDirection midiDirection) {
        this.midiDirection = midiDirection;
    }

    public List<MidiSource> getMidiSources() {
        return midiSources;
    }

    public void setMidiSources(List<MidiSource> midiSources) {
        this.midiSources = midiSources;
    }

    public List<MidiDestination> getMidiDestinations() {
        return midiDestinations;
    }

    public void setMidiDestinations(List<MidiDestination> midiDestinations) {
        this.midiDestinations = midiDestinations;
    }
}
