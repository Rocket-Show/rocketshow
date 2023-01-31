package com.ascargon.rocketshow.lighting;

import javax.sound.midi.ShortMessage;

public interface Midi2LightingConvertService {

    void processMidiEvent(ShortMessage shortMessage, Midi2LightingMapping midi2LightingMapping, LightingUniverse lightingUniverse);

}
