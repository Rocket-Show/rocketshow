package com.ascargon.rocketshow.dmx;

import com.ascargon.rocketshow.midi.MidiSignal;

public interface Midi2DmxConvertService {

    void processMidiEvent(MidiSignal midiSignal, Midi2DmxMapping midi2DmxMapping, DmxUniverse dmxUniverse);

}
