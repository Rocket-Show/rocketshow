package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

@Service
public interface MidiControlActionExecutionService {

    void processMidiSignal(MidiSignal midiSignal) throws Exception;

}
