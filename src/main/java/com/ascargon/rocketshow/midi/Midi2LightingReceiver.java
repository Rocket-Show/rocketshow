package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/**
 * Receive MIDI messages and map them to lighting signals.
 *
 * @author Moritz A. Vieli
 */
class Midi2LightingReceiver implements Receiver {

    private final static Logger logger = LoggerFactory.getLogger(Midi2LightingReceiver.class);

    private MidiMapping midiMapping;
    private Midi2LightingMapping midi2LightingMapping;
    private final Midi2LightingConvertService midi2LightingConvertService;
    private final LightingService lightingService;

    private final LightingUniverse lightingUniverse;

    public Midi2LightingReceiver(Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService) {
        this.midi2LightingConvertService = midi2LightingConvertService;
        this.lightingService = lightingService;

        lightingUniverse = new LightingUniverse();

        lightingService.addLightingUniverse(lightingUniverse);
    }

    @Override
    public void send(MidiMessage midiMessage, long timeStamp) {
        // Map the MIDI message to a lighting signal
        if (!(midiMessage instanceof ShortMessage)) {
            return;
        }

        ShortMessage shortMessage = (ShortMessage) midiMessage;

        try {
            MidiMapper.processMidiEvent(shortMessage, midiMapping);
        } catch (InvalidMidiDataException e) {
            logger.error("Could not process MIDI event to lighting", e);
        }

        midi2LightingConvertService.processMidiEvent(shortMessage, midi2LightingMapping, lightingUniverse);
    }

    public void setMidi2LightingMapping(Midi2LightingMapping midi2LightingMapping) {
        this.midi2LightingMapping = midi2LightingMapping;
    }

    public MidiMapping getMidiMapping() {
        return midiMapping;
    }

    public void setMidiMapping(MidiMapping midiMapping) {
        this.midiMapping = midiMapping;
    }

    @Override
    public void close() {
        lightingService.removeLightingUniverse(lightingUniverse);
    }

}
