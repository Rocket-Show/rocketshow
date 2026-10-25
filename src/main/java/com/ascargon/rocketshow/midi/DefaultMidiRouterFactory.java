package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.settings.MidiRoutingsChangedEvent;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Service
public class DefaultMidiRouterFactory implements MidiRouterFactory {

    // A router for routings in the settings and how to get them from the settings
    private record SettingsMidiRouter(MidiRouter midiRouter, Function<Settings, List<MidiRouting>> midiRoutingListOfSettings) {
    }

    private final SettingsService settingsService;
    private final Midi2LightingConvertService midi2LightingConvertService;
    private final LightingService lightingService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final ActivityNotificationMidiService activityNotificationMidiService;

    private final List<SettingsMidiRouter> settingsMidiRouterList = new CopyOnWriteArrayList<>();

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

    @Override
    public MidiRouter getSettingsMidiRouter(Function<Settings, List<MidiRouting>> midiRoutingListOfSettings) {
        MidiRouter midiRouter = getMidiRouter(midiRoutingListOfSettings.apply(settingsService.getSettings()));
        settingsMidiRouterList.add(new SettingsMidiRouter(midiRouter, midiRoutingListOfSettings));
        return midiRouter;
    }

    @EventListener
    public void midiRoutingsChanged(MidiRoutingsChangedEvent event) {
        // Saving the settings replaces them, so route with the routings of the new settings
        for (SettingsMidiRouter settingsMidiRouter : settingsMidiRouterList) {
            settingsMidiRouter.midiRouter().setMidiRoutingList(settingsMidiRouter.midiRoutingListOfSettings().apply(settingsService.getSettings()));
        }
    }

}
