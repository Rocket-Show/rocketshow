package com.ascargon.rocketshow.midi;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiUnavailableException;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceOutService {

    void reconnectMidiDevice();

    javax.sound.midi.MidiDevice getMidiDevice();

    SerialPort getMidiSerialDevice();
}
