package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;
import purejavacomm.SerialPort;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceOutService {

    void reconnectMidiDevice();

    javax.sound.midi.MidiDevice getMidiDevice();

    SerialPort getMidiSerialDevice();
}
