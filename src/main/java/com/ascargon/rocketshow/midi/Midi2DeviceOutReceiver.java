package com.ascargon.rocketshow.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

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

        midiDeviceOutService.sendMessage(message);
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
