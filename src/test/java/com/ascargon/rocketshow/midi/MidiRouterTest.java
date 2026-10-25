package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.Midi2LightingConvertService;
import com.ascargon.rocketshow.lighting.Midi2LightingMapping;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MidiRouterTest {

    private final SettingsService settingsService = mock(SettingsService.class);
    private final Midi2LightingConvertService midi2LightingConvertService = mock(Midi2LightingConvertService.class);
    private final LightingService lightingService = mock(LightingService.class);
    private final MidiDeviceOutService midiDeviceOutService = mock(MidiDeviceOutService.class);
    private final ActivityNotificationMidiService activityNotificationMidiService = mock(ActivityNotificationMidiService.class);
    private final RemoteDevice remoteDevice = mock(RemoteDevice.class);

    private final Settings settings = new Settings();

    // The messages as they arrived at each destination (recorded at call time, because messages are mutable)
    private final List<String> outDeviceMessages = new ArrayList<>();
    private final List<String> lightingMessages = new ArrayList<>();
    private final List<String> remoteMessages = new ArrayList<>();
    private final List<String> monitorOutMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        settings.setMidiMapping(new MidiMapping());
        when(settingsService.getSettings()).thenReturn(settings);
        when(settingsService.getRemoteDeviceByName("Unit B")).thenReturn(remoteDevice);
        when(midiDeviceOutService.getMidiDevice()).thenReturn(mock(javax.sound.midi.MidiDevice.class));

        doAnswer(invocation -> outDeviceMessages.add(toHex(invocation.getArgument(0))))
                .when(midiDeviceOutService).sendMessage(any());
        doAnswer(invocation -> lightingMessages.add(toHex(invocation.getArgument(0))))
                .when(midi2LightingConvertService).processMidiEvent(any(), any(), any());
        doAnswer(invocation -> {
            MidiSignal midiSignal = invocation.getArgument(0);
            return remoteMessages.add(toHex(midiSignal.getShortMessage()));
        }).when(remoteDevice).sendMidiSignal(any());
        doAnswer(invocation -> monitorOutMessages.add(toHex(invocation.getArgument(0))))
                .when(activityNotificationMidiService).notifyClients(any(), eq(MidiDirection.OUT), any(), any());
    }

    private String toHex(MidiMessage midiMessage) {
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(midiMessage.getMessage(), 0, midiMessage.getLength());
    }

    private MidiMapping getMidiMapping(int channelOffset, int noteOffset) {
        MidiMapping midiMapping = new MidiMapping();
        midiMapping.setChannelOffset(channelOffset);
        midiMapping.setNoteOffset(noteOffset);
        return midiMapping;
    }

    private MidiRouting getMidiRouting(MidiDestination midiDestination, MidiMapping midiMapping) {
        MidiRouting midiRouting = new MidiRouting();
        midiRouting.setMidiDestination(midiDestination);
        midiRouting.setMidiMapping(midiMapping);

        if (midiDestination == MidiDestination.REMOTE) {
            midiRouting.getRemoteDeviceIdList().add("Unit B");
        }

        return midiRouting;
    }

    private MidiRouter getMidiRouter(MidiRouting... midiRoutings) {
        return new MidiRouter(settingsService, midi2LightingConvertService, lightingService, midiDeviceOutService, activityNotificationMidiService, List.of(midiRoutings));
    }

    private ShortMessage getNoteOn() throws Exception {
        return new ShortMessage(ShortMessage.NOTE_ON, 0, 0x3C, 0x64);
    }

    @Test
    void routesToTheOutDevice() throws Exception {
        getMidiRouter(getMidiRouting(MidiDestination.OUT_DEVICE, new MidiMapping())).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("90 3C 64"), outDeviceMessages);
    }

    @Test
    void routesToLighting() throws Exception {
        MidiRouting midiRouting = getMidiRouting(MidiDestination.LIGHTING, new MidiMapping());
        Midi2LightingMapping midi2LightingMapping = new Midi2LightingMapping();
        midiRouting.setMidi2LightingMapping(midi2LightingMapping);
        midiRouting.setUniverseUuid("universe-1");

        getMidiRouter(midiRouting).sendSignal(getNoteOn(), MidiSource.MIDI_FILE);

        assertEquals(List.of("90 3C 64"), lightingMessages);
        verify(midi2LightingConvertService).processMidiEvent(any(), eq(midi2LightingMapping), any(LightingUniverseState.class));
    }

    @Test
    void routesToRemoteDevices() throws Exception {
        getMidiRouter(getMidiRouting(MidiDestination.REMOTE, new MidiMapping())).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("90 3C 64"), remoteMessages);
    }

    @Test
    void routesToEveryRouting() throws Exception {
        getMidiRouter(
                getMidiRouting(MidiDestination.OUT_DEVICE, new MidiMapping()),
                getMidiRouting(MidiDestination.LIGHTING, new MidiMapping()),
                getMidiRouting(MidiDestination.REMOTE, new MidiMapping())
        ).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("90 3C 64"), outDeviceMessages);
        assertEquals(List.of("90 3C 64"), lightingMessages);
        assertEquals(List.of("90 3C 64"), remoteMessages);
    }

    @Test
    void appliesOnlyTheOwnMappingOfEachRouting() throws Exception {
        getMidiRouter(
                getMidiRouting(MidiDestination.OUT_DEVICE, getMidiMapping(0, 12)),
                getMidiRouting(MidiDestination.REMOTE, getMidiMapping(2, 1))
        ).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("90 48 64"), outDeviceMessages);
        assertEquals(List.of("92 3D 64"), remoteMessages);
    }

    @Test
    void appliesTheGlobalMappingToEveryRouting() throws Exception {
        settings.setMidiMapping(getMidiMapping(1, 12));

        getMidiRouter(
                getMidiRouting(MidiDestination.OUT_DEVICE, new MidiMapping()),
                getMidiRouting(MidiDestination.LIGHTING, new MidiMapping()),
                getMidiRouting(MidiDestination.REMOTE, getMidiMapping(0, 1))
        ).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        assertEquals(List.of("91 48 64"), outDeviceMessages);
        assertEquals(List.of("91 48 64"), lightingMessages);
        assertEquals(List.of("91 49 64"), remoteMessages);
    }

    @Test
    void appliesTheGlobalMappingOnceToCompositionRoutings() throws Exception {
        settings.setMidiMapping(getMidiMapping(1, 12));

        // Like the composition pipeline: routing mapping -> composition mapping -> global mapping
        MidiMapping compositionMidiMapping = new MidiMapping();
        compositionMidiMapping.setParent(settings.getMidiMapping());
        MidiRouting midiRouting = getMidiRouting(MidiDestination.OUT_DEVICE, new MidiMapping());
        midiRouting.getMidiMapping().setParent(compositionMidiMapping);

        getMidiRouter(midiRouting).sendSignal(getNoteOn(), MidiSource.MIDI_FILE);

        assertEquals(List.of("91 48 64"), outDeviceMessages);
    }

    @Test
    void ignoresTheGlobalMappingWhenOverridingIt() throws Exception {
        settings.setMidiMapping(getMidiMapping(1, 12));

        MidiMapping compositionMidiMapping = new MidiMapping();
        compositionMidiMapping.setParent(settings.getMidiMapping());
        MidiMapping midiMapping = getMidiMapping(0, 1);
        midiMapping.setOverrideParent(true);
        midiMapping.setParent(compositionMidiMapping);

        getMidiRouter(getMidiRouting(MidiDestination.OUT_DEVICE, midiMapping)).sendSignal(getNoteOn(), MidiSource.MIDI_FILE);

        assertEquals(List.of("90 3D 64"), outDeviceMessages);
    }

    @Test
    void keepsTheOriginalMessage() throws Exception {
        ShortMessage noteOn = getNoteOn();

        getMidiRouter(getMidiRouting(MidiDestination.OUT_DEVICE, getMidiMapping(1, 12))).sendSignal(noteOn, MidiSource.ACTION);

        assertEquals("90 3C 64", toHex(noteOn));
    }

    @Test
    void notifiesTheMonitorAboutEachRouting() throws Exception {
        ShortMessage noteOn = getNoteOn();

        getMidiRouter(getMidiRouting(MidiDestination.OUT_DEVICE, getMidiMapping(0, 12))).sendSignal(noteOn, MidiSource.IN_DEVICE);

        verify(activityNotificationMidiService).notifyClients(eq(noteOn), eq(MidiDirection.IN), eq(MidiSource.IN_DEVICE), isNull());
        verify(activityNotificationMidiService).notifyClients(any(), eq(MidiDirection.OUT), eq(MidiSource.IN_DEVICE), eq(MidiDestination.OUT_DEVICE));
        assertEquals(List.of("90 48 64"), monitorOutMessages);
    }

    @Test
    void skipsTheOutDeviceWhenNoneIsConnected() throws Exception {
        when(midiDeviceOutService.getMidiDevice()).thenReturn(null);
        when(midiDeviceOutService.getMidiSerialDevice()).thenReturn(null);

        getMidiRouter(getMidiRouting(MidiDestination.OUT_DEVICE, new MidiMapping())).sendSignal(getNoteOn(), MidiSource.IN_DEVICE);

        verify(midiDeviceOutService, never()).sendMessage(any());
    }

    @Test
    void sendsSysExOnlyToTheOutDevice() throws Exception {
        byte[] data = HexFormat.of().parseHex("F07E7F0601F7");

        getMidiRouter(
                getMidiRouting(MidiDestination.OUT_DEVICE, getMidiMapping(1, 12)),
                getMidiRouting(MidiDestination.LIGHTING, new MidiMapping()),
                getMidiRouting(MidiDestination.REMOTE, new MidiMapping())
        ).sendSignal(new SysexMessage(data, data.length), MidiSource.IN_DEVICE);

        assertEquals(List.of("F0 7E 7F 06 01 F7"), outDeviceMessages);
        assertEquals(List.of(), lightingMessages);
        assertEquals(List.of(), remoteMessages);
    }

    @Test
    void closesTheLightingUniverses() {
        getMidiRouter(getMidiRouting(MidiDestination.LIGHTING, new MidiMapping())).close();

        verify(lightingService).addLightingUniverse(any());
        verify(lightingService).removeLightingUniverse(any());
    }

}
