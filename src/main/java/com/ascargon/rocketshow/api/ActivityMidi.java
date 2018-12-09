package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.UpdateService;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.transform.Source;

@XmlRootElement
public class ActivityMidi {

    public enum MidiSource {
        DEVICE_IN,
        MIDI_FILE,
        REMOTE_DEVICE
    }

    private MidiSignal midiSignal;

    private MidiSource midiSource;

    public MidiSignal getMidiSignal() {
        return midiSignal;
    }

    public void setMidiSignal(MidiSignal midiSignal) {
        this.midiSignal = midiSignal;
    }

    public MidiSource getMidiSource() {
        return midiSource;
    }

    public void setMidiSource(MidiSource midiSource) {
        this.midiSource = midiSource;
    }

}
