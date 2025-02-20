package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.midi.MidiDestination;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiSource;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement
@Getter
@Setter
public class ActivityMidi {

    private MidiSignal midiSignal;
    private MidiDirection midiDirection;
    private List<MidiSource> midiSources = new ArrayList<>();
    private List<MidiDestination> midiDestinations = new ArrayList<>();

}
