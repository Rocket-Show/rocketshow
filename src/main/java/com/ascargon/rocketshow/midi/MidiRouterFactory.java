package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.Settings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Service
public interface MidiRouterFactory {

    MidiRouter getMidiRouter(List<MidiRouting> midiRoutingList);

    // A router for routings in the settings (e.g. of the MIDI input), which follows changes of the settings
    MidiRouter getSettingsMidiRouter(Function<Settings, List<MidiRouting>> midiRoutingListOfSettings);

}
