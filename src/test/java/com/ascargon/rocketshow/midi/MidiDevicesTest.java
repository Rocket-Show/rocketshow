package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Connecting the MIDI devices of the settings (e.g. a keyboard by USB), on a simulated MIDI interface
 * ({@link TestMidiDeviceProvider}).
 */
class MidiDevicesTest {

    private final static TestMidiDeviceProvider.TestMidiDevice INTERFACE = TestMidiDeviceProvider.INTERFACE;

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final DefaultMidiService midiService = new DefaultMidiService();
    private final ActionMidiExecutionService actionMidiExecutionService = mock(ActionMidiExecutionService.class);
    private final MidiRouter midiRouter = mock(MidiRouter.class);
    private DefaultMidiDeviceOutService outService;
    private DefaultMidiDeviceInService inService;

    @BeforeEach
    void setUp() {
        INTERFACE.reset();
        when(settingsService.getSettings()).thenReturn(settings);
    }

    @AfterEach
    void tearDown() throws Exception {
        INTERFACE.reset();
    }

    // The device as the settings page offers it
    private MidiDevice interfaceOfTheSettings(MidiDirection midiDirection) throws Exception {
        return midiService.getMidiDevices(midiDirection).stream()
                .filter(midiDevice -> midiDevice.getName().equals(INTERFACE.getDeviceInfo().getName()))
                .findFirst().orElseThrow();
    }

    private DefaultMidiDeviceOutService startOut() {
        outService = new DefaultMidiDeviceOutService(settingsService, midiService);
        outService.init();
        return outService;
    }

    private DefaultMidiDeviceInService startIn() {
        MidiRouterFactory midiRouterFactory = mock(MidiRouterFactory.class);
        when(midiRouterFactory.getSettingsMidiRouter(any())).thenReturn(midiRouter);
        inService = new DefaultMidiDeviceInService(settingsService, actionMidiExecutionService, midiService, midiRouterFactory);
        inService.init();
        return inService;
    }

    @Test
    void offersTheMidiDevicesButNotTheSystemOnes() throws Exception {
        List<String> names = midiService.getMidiDevices(MidiDirection.IN).stream().map(MidiDevice::getName).toList();

        assertTrue(names.contains(INTERFACE.getDeviceInfo().getName()), names.toString());
        assertFalse(names.contains("Gervill"));
        assertFalse(names.contains("Real Time Sequencer"));
    }

    @Test
    void sendsToTheMidiOutDevice() throws Exception {
        settings.setMidiOutDevice(interfaceOfTheSettings(MidiDirection.OUT));

        startOut();
        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        outService.sendMessage(noteOn);

        assertTrue(outService.isConnected());
        assertTrue(INTERFACE.isOpen());
        assertEquals(List.of(noteOn), INTERFACE.received);
    }

    @Test
    void sendsAShowOfMidiThroughOneReceiver() throws Exception {
        settings.setMidiOutDevice(interfaceOfTheSettings(MidiDirection.OUT));
        startOut();

        // E.g. MIDI clock for a while
        for (int i = 0; i < 1000; i++) {
            outService.sendMessage(new ShortMessage(ShortMessage.TIMING_CLOCK));
        }

        assertEquals(1000, INTERFACE.received.size());
        assertEquals(1, INTERFACE.getReceivers().size());

        outService.reconnectMidiDevice();
        outService.sendMessage(new ShortMessage(ShortMessage.TIMING_CLOCK));
        assertEquals(1, INTERFACE.getReceivers().size());
    }

    @Test
    void receivesFromTheMidiInDevice() throws Exception {
        settings.setMidiInDevice(interfaceOfTheSettings(MidiDirection.IN));

        startIn();
        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        INTERFACE.play(noteOn);

        verify(actionMidiExecutionService).processMidiSignal(noteOn);
        verify(midiRouter).sendSignal(noteOn, MidiSource.IN_DEVICE);
    }

    @Test
    void findsTheDeviceByItsNameAfterOthersWereConnected() throws Exception {
        // The device got another position, e.g. another interface was plugged in before it
        MidiDevice midiDevice = interfaceOfTheSettings(MidiDirection.OUT);
        midiDevice.setId(midiDevice.getId() + 5);
        settings.setMidiOutDevice(midiDevice);

        startOut();

        assertTrue(outService.isConnected());
    }

    @Test
    void reconnectsTheMidiInDevice() throws Exception {
        settings.setMidiInDevice(interfaceOfTheSettings(MidiDirection.IN));
        startIn();

        inService.reconnectMidiDevice();
        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);
        INTERFACE.play(noteOn);

        // Once, not by the old connection as well
        verify(actionMidiExecutionService).processMidiSignal(noteOn);
    }

}
