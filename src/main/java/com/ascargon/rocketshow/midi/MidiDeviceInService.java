package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceInService {

    void reconnectMidiDevice() throws MidiUnavailableException;

    javax.sound.midi.MidiDevice getMidiInDevice();

}
