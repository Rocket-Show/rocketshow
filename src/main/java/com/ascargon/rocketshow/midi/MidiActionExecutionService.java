package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public interface MidiActionExecutionService {

    void processMidiSignal(ShortMessage shortMessage) throws Exception;

}
