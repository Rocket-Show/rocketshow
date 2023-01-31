package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;

@Service
public interface MidiControlActionExecutionService {

    void processMidiSignal(MidiMessage midiMessage) throws Exception;

}
