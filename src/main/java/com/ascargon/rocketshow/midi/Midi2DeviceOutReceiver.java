package com.ascargon.rocketshow.midi;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Receive MIDI messages and send them to the out device.
 *
 * @author Moritz A. Vieli
 */
public class Midi2DeviceOutReceiver implements Receiver {

    private final static Logger logger = LogManager.getLogger(Midi2DeviceOutReceiver.class);

    private MidiDeviceService midiDeviceService;

    private MidiMapping midiMapping;

    Midi2DeviceOutReceiver(MidiDeviceService midiDeviceService) {
        this.midiDeviceService = midiDeviceService;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (midiDeviceService.getMidiOutDevice() == null) {
            return;
        }

        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);
        MidiMapper.processMidiEvent(midiSignal, midiMapping);

        try {
            midiDeviceService.getMidiOutDevice().getReceiver().send(midiSignal.getShortMessage(), -1);
        } catch (Exception e) {
            logger.error("Could not send MIDI signal to out device receiver", e);
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
