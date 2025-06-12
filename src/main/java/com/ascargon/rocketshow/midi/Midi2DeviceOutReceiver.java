package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import purejavacomm.SerialPort;

import java.io.OutputStream;

/**
 * Receive MIDI messages and send them to the out device.
 *
 * @author Moritz A. Vieli
 */
class Midi2DeviceOutReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(Midi2DeviceOutReceiver.class);
    private final MidiDeviceOutService midiDeviceOutService;
    private MidiMapping midiMapping;

    Midi2DeviceOutReceiver(MidiDeviceOutService midiDeviceOutService) {
        this.midiDeviceOutService = midiDeviceOutService;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (midiDeviceOutService.getMidiDevice() == null && midiDeviceOutService.getMidiSerialDevice() == null) {
            // No MIDI OUT device available
            return;
        }

        try {
            MidiMapper.processMidiEvent(message, midiMapping);
        } catch (InvalidMidiDataException e) {
            logger.error("Could not process MIDI event to device out", e);
        }

        if (midiDeviceOutService.getMidiDevice() != null) {
            // Real MIDI device
            MidiDevice midiDevice = midiDeviceOutService.getMidiDevice();

            try {
                midiDevice.getReceiver().send(message, -1);
            } catch (Exception e) {
                logger.error("Could not send MIDI signal to out device receiver " + midiDevice.getDeviceInfo().getName(), e);
            }
        } else if (midiDeviceOutService.getMidiSerialDevice() != null) {
            // MIDI serial device
            SerialPort midiSerialDevice = midiDeviceOutService.getMidiSerialDevice();

            try {
                byte[] data = message.getMessage();
                OutputStream output = midiSerialDevice.getOutputStream();
                output.write(data);
                output.flush(); // optional, but ensures immediate transmission
            } catch (Exception e) {
                logger.error("Could not send MIDI signal to serial out device receiver", e);
            }
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }

    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

}
