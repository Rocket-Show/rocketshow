package com.ascargon.rocketshow.midi;

import org.springframework.stereotype.Service;
import purejavacomm.SerialPort;

import javax.sound.midi.MidiMessage;

/**
 * Handle locally connected MIDI in devices.
 */
@Service
public interface MidiDeviceOutService {

    void reconnectMidiDevice();

    javax.sound.midi.MidiDevice getMidiDevice();

    SerialPort getMidiSerialDevice();

    boolean isConnected();

    void sendMessage(MidiMessage message);

    // Like sendMessage, but throws when the message can't be sent, e.g. for the self-test
    void sendMessageChecked(MidiMessage message) throws Exception;
}
