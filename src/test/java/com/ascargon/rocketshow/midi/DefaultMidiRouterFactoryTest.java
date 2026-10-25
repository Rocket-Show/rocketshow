package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.settings.MidiRoutingsChangedEvent;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultMidiRouterFactoryTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final MidiDeviceOutService midiDeviceOutService = mock(MidiDeviceOutService.class);

    private final DefaultMidiRouterFactory midiRouterFactory = new DefaultMidiRouterFactory(settingsService, mock(Midi2LightingConvertService.class), mock(LightingService.class), midiDeviceOutService, mock(ActivityNotificationMidiService.class));

    // The messages as they arrived at the out device (recorded at call time, because messages are mutable)
    private final List<String> outDeviceMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(midiDeviceOutService.getMidiDevice()).thenReturn(mock(javax.sound.midi.MidiDevice.class));
        doAnswer(invocation -> outDeviceMessages.add(toHex(invocation.getArgument(0))))
                .when(midiDeviceOutService).sendMessage(any());
    }

    private String toHex(MidiMessage midiMessage) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(midiMessage.getMessage(), 0, midiMessage.getLength());
    }

    // New settings, like the ones saved in the web app
    private Settings getSettings(int globalNoteOffset, MidiDestination... midiDestinations) {
        MidiMapping midiMapping = new MidiMapping();
        midiMapping.setNoteOffset(globalNoteOffset);

        Settings settings = new Settings();
        settings.setMidiMapping(midiMapping);

        for (MidiDestination midiDestination : midiDestinations) {
            MidiRouting midiRouting = new MidiRouting();
            midiRouting.setMidiDestination(midiDestination);
            settings.getDeviceInMidiRoutingList().add(midiRouting);
        }

        return settings;
    }

    private ShortMessage getNoteOn() throws Exception {
        return new ShortMessage(ShortMessage.NOTE_ON, 0, 0x3C, 0x64);
    }

    @Test
    void appliesChangedSettingsToTheRoutersOfTheSettings() throws Exception {
        when(settingsService.getSettings()).thenReturn(getSettings(0, MidiDestination.OUT_DEVICE));
        MidiRouter midiRouter = midiRouterFactory.getSettingsMidiRouter(Settings::getDeviceInMidiRoutingList);
        midiRouter.sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        // Another global mapping
        when(settingsService.getSettings()).thenReturn(getSettings(12, MidiDestination.OUT_DEVICE));
        midiRouterFactory.midiRoutingsChanged(new MidiRoutingsChangedEvent());
        midiRouter.sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        // No routing to the out device anymore
        when(settingsService.getSettings()).thenReturn(getSettings(12, MidiDestination.LIGHTING));
        midiRouterFactory.midiRoutingsChanged(new MidiRoutingsChangedEvent());
        midiRouter.sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("90 3C 64", "90 48 64"), outDeviceMessages);
    }

    @Test
    void keepsTheRoutingsOfOtherRouters() throws Exception {
        // E.g. the routings of a composition
        when(settingsService.getSettings()).thenReturn(getSettings(0));
        MidiRouting midiRouting = new MidiRouting();
        midiRouting.setMidiDestination(MidiDestination.OUT_DEVICE);
        MidiRouter midiRouter = midiRouterFactory.getMidiRouter(List.of(midiRouting));

        when(settingsService.getSettings()).thenReturn(getSettings(0, MidiDestination.LIGHTING));
        midiRouterFactory.midiRoutingsChanged(new MidiRoutingsChangedEvent());
        midiRouter.sendSignal(getNoteOn(), MidiSource.MIDI_FILE);

        assertEquals(List.of("90 3C 64"), outDeviceMessages);
    }

}
