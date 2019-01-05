package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.midi.MidiSignal;

public interface Midi2LightingConvertService {

    void processMidiEvent(MidiSignal midiSignal, Midi2LightingMapping midi2LightingMapping, LightingUniverse lightingUniverse);

}
