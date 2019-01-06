package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.lighting.Midi2LightingMapping.MappingType;
import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultMidi2LightingConvertService implements Midi2LightingConvertService {

    private final DefaultLightingService defaultLightingService;

    public DefaultMidi2LightingConvertService(DefaultLightingService defaultLightingService) {
        this.defaultLightingService = defaultLightingService;
    }

    private void mapSimple(MidiSignal midiSignal, LightingUniverse lightingUniverse) {
        if (midiSignal.getCommand() == ShortMessage.NOTE_ON) {
            int valueTo = midiSignal.getVelocity() * 2;

            // Extend the last note to the max
            // TODO enable this feature by a mapping-setting
            if (valueTo == 254) {
                valueTo = 255;
            }

            lightingUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
            defaultLightingService.send();
        } else if (midiSignal.getCommand() == ShortMessage.NOTE_OFF) {
            int valueTo = 0;

            lightingUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
            defaultLightingService.send();
        }
    }

    private void mapExact() {
        // TODO
    }

    @Override
    public void processMidiEvent(MidiSignal midiSignal, Midi2LightingMapping midi2LightingMapping, LightingUniverse lightingUniverse) {
        // Map the MIDI event and send the appropriate lighting signal

        // Only react to NOTE_ON/NOTE_OFF events
        if (midiSignal.getCommand() != ShortMessage.NOTE_ON && midiSignal.getCommand() != ShortMessage.NOTE_OFF) {
            return;
        }

        if (midi2LightingMapping.getMappingType() == MappingType.SIMPLE) {
            mapSimple(midiSignal, lightingUniverse);
        } else if (midi2LightingMapping.getMappingType() == MappingType.EXACT) {
            mapExact();
        }
    }

}
