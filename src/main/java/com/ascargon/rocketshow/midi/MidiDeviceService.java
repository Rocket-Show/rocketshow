package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceService {

    void reconnectMidiDevices() throws MidiUnavailableException;

    javax.sound.midi.MidiDevice getMidiInDevice();

    javax.sound.midi.MidiDevice getMidiOutDevice();

}
