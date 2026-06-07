package com.ascargon.rocketshow.lighting;

import com.ascargon.rocketshow.lighting.Midi2LightingMapping.MappingType;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import javax.sound.midi.ShortMessage;

@Service
public class DefaultMidi2LightingConvertService implements Midi2LightingConvertService {

    private final LightingService lightingService;

    public DefaultMidi2LightingConvertService(LightingService lightingService) {
        this.lightingService = lightingService;
    }

    private void sendLightingUniverse() {
        lightingService.send();
    }

    private void mapSimple(ShortMessage shortMessage, LightingUniverseState lightingUniverse) {
        if (shortMessage.getCommand() == ShortMessage.NOTE_ON) {
            int valueTo = shortMessage.getData2() * 2;

            // Extend the last note to the max
            // TODO enable this feature by a mapping-setting
            if (valueTo == 254) {
                valueTo = 255;
            }

            lightingUniverse.getUniverse().put(shortMessage.getData1(), valueTo);
            sendLightingUniverse();
        } else if (shortMessage.getCommand() == ShortMessage.NOTE_OFF) {
            int valueTo = 0;

            lightingUniverse.getUniverse().put(shortMessage.getData1(), valueTo);
            sendLightingUniverse();
        }
    }

    private void mapExact() {
        // TODO
    }

    @Override
    public void processMidiEvent(ShortMessage shortMessage, Midi2LightingMapping midi2LightingMapping, LightingUniverseState lightingUniverse) {
        // Map the MIDI event and send the appropriate lighting signal

        // Only react to NOTE_ON/NOTE_OFF events
        if (shortMessage.getCommand() != ShortMessage.NOTE_ON && shortMessage.getCommand() != ShortMessage.NOTE_OFF) {
            return;
        }

        if (midi2LightingMapping.getMappingType() == MappingType.SIMPLE) {
            mapSimple(shortMessage, lightingUniverse);
        } else if (midi2LightingMapping.getMappingType() == MappingType.EXACT) {
            mapExact();
        }
    }

}
