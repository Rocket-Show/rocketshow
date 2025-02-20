package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultMidiRouterFactory implements MidiRouterFactory {

    private final SettingsService settingsService;
    private final Midi2LightingConvertService midi2LightingConvertService;
    private final LightingService lightingService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final ActivityNotificationMidiService activityNotificationMidiService;

    public DefaultMidiRouterFactory(SettingsService settingsService, Midi2LightingConvertService midi2LightingConvertService, LightingService lightingService, MidiDeviceOutService midiDeviceOutService, ActivityNotificationMidiService activityNotificationMidiService) {
        this.settingsService = settingsService;
        this.midi2LightingConvertService = midi2LightingConvertService;
        this.lightingService = lightingService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.activityNotificationMidiService = activityNotificationMidiService;
    }

    @Override
    public MidiRouter getMidiRouter(List<MidiRouting> midiRoutingList) {
        return new MidiRouter(settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService, activityNotificationMidiService, midiRoutingList);
    }

}
