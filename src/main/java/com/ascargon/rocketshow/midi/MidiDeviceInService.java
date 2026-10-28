package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceInService {

    void reconnectMidiDevice() throws MidiUnavailableException;

    javax.sound.midi.MidiDevice getMidiDevice();

    boolean isConnected();

    // Sends the MIDI input only to this receiver, instead of the MIDI triggers and the routings (e.g. during the
    // self-test). Null to execute and route it again.
    void divert(Receiver receiver);

}
