package com.ascargon.rocketshow.dmx;

import com.ascargon.rocketshow.dmx.Midi2DmxMapping.MappingType;
import com.ascargon.rocketshow.midi.MidiSignal;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultMidi2DmxConvertService implements Midi2DmxConvertService {

    private DefaultDmxService defaultDmxService;

    public DefaultMidi2DmxConvertService(DefaultDmxService defaultDmxService) {
        this.defaultDmxService = defaultDmxService;
    }

    private void mapSimple(MidiSignal midiSignal, DmxUniverse dmxUniverse) {
        if (midiSignal.getCommand() == ShortMessage.NOTE_ON) {
            int valueTo = midiSignal.getVelocity() * 2;

            // Extend the last note to the max
            // TODO enable this feature by a mapping-setting
            if (valueTo == 254) {
                valueTo = 255;
            }

            dmxUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
            defaultDmxService.send();
        } else if (midiSignal.getCommand() == ShortMessage.NOTE_OFF) {
            int valueTo = 0;

            dmxUniverse.getUniverse().put(midiSignal.getNote(), valueTo);
            defaultDmxService.send();
        }
    }

    private void mapExact() {
        // TODO
    }

    @Override
    public void processMidiEvent(MidiSignal midiSignal, Midi2DmxMapping midi2DmxMapping, DmxUniverse dmxUniverse) {
        // Map the MIDI event and send the appropriate DMX signal

        // Only react to NOTE_ON/NOTE_OFF events
        if (midiSignal.getCommand() != ShortMessage.NOTE_ON && midiSignal.getCommand() != ShortMessage.NOTE_OFF) {
            return;
        }

        if (midi2DmxMapping.getMappingType() == MappingType.SIMPLE) {
            mapSimple(midiSignal, dmxUniverse);
        } else if (midi2DmxMapping.getMappingType() == MappingType.EXACT) {
            mapExact();
        }
    }

}
