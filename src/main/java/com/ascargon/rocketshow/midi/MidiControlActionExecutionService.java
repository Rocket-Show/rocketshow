package com.ascargon.rocketshow.midi;

public interface MidiControlActionExecutionService {

    void processMidiSignal(MidiSignal midiSignal) throws Exception;

}
