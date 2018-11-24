package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.dmx.DmxUniverse;
import com.ascargon.rocketshow.dmx.Midi2DmxConvertService;
import com.ascargon.rocketshow.dmx.Midi2DmxMapping;
import com.ascargon.rocketshow.midi.MidiMapper;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiSignal;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/**
 * Receive MIDI messages and map them to DMX signals.
 *
 * @author Moritz A. Vieli
 */
class Midi2DmxReceiver implements Receiver {

    private MidiMapping midiMapping;
    private Midi2DmxMapping midi2DmxMapping;
    private final Midi2DmxConvertService midi2DmxConvertService;
    private final DmxService dmxService;

    private final DmxUniverse dmxUniverse;

    public Midi2DmxReceiver(Midi2DmxConvertService midi2DmxConvertService, DmxService dmxService) {
        this.midi2DmxConvertService = midi2DmxConvertService;
        this.dmxService = dmxService;

        dmxUniverse = new DmxUniverse();

        dmxService.addDmxUniverse(dmxUniverse);
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        // Map the midi to DMX out
        if (!(message instanceof ShortMessage)) {
            return;
        }

        MidiSignal midiSignal = new MidiSignal((ShortMessage) message);

        MidiMapper.processMidiEvent(midiSignal, midiMapping);

        midi2DmxConvertService.processMidiEvent(midiSignal, midi2DmxMapping, dmxUniverse);
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
        dmxService.removeDmxUniverse(dmxUniverse);
    }

}
