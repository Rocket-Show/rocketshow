package com.ascargon.rocketshow.dmx;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.midi.MidiMapper;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiSignal;

/**
 * Receive MIDI messages and map them to DMX signals.
 *
 * @author Moritz A. Vieli
 */
public class Midi2DmxReceiver implements Receiver {

    private MidiMapping midiMapping;
    private Midi2DmxMapping midi2DmxMapping;
    private Midi2DmxConverter midi2DmxConverter;
    private DmxManager dmxManager;

    private DmxUniverse dmxUniverse;

    public Midi2DmxReceiver(Manager manager) {
        this.midi2DmxConverter = manager.getMidi2DmxConverter();
        this.dmxManager = manager.getDmxManager();

        dmxUniverse = new DmxUniverse();

        dmxManager.addDmxUniverse(dmxUniverse);
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        // Map the midi to DMX out
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);

        MidiMapper.processMidiEvent(midiSignal, midiMapping);

        midi2DmxConverter.processMidiEvent(midiSignal, midi2DmxMapping, dmxUniverse);
    }

    public void setMidi2DmxMapping(Midi2DmxMapping midi2DmxMapping) {
        this.midi2DmxMapping = midi2DmxMapping;
    }

    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

    @Override
    public void close() {
        dmxManager.removeDmxUniverse(dmxUniverse);
    }

}
