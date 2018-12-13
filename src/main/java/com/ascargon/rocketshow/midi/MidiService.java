package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiUnavailableException;
import java.util.List;

public interface MidiService {

    javax.sound.midi.MidiDevice getHardwareMidiDevice(com.ascargon.rocketshow.midi.MidiDevice midiDevice,
                                                      MidiSignal.MidiDirection midiDirection) throws MidiUnavailableException;

    List<MidiDevice> getMidiDevices(MidiSignal.MidiDirection midiDirection)
            throws MidiUnavailableException;

}
