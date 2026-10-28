package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.midi.ActionMidiExecutionService;
import com.ascargon.rocketshow.midi.DefaultMidiDeviceInService;
import com.ascargon.rocketshow.midi.DefaultMidiDeviceOutService;
import com.ascargon.rocketshow.midi.DefaultMidiService;
import com.ascargon.rocketshow.midi.MidiDevice;
import com.ascargon.rocketshow.midi.MidiDirection;
import com.ascargon.rocketshow.midi.MidiRouter;
import com.ascargon.rocketshow.midi.MidiRouterFactory;
import com.ascargon.rocketshow.midi.TestMidiDeviceProvider;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The MIDI checks of the self-test, on a simulated MIDI interface ({@link TestMidiDeviceProvider}) with or without
 * a loopback cable.
 */
class MidiSelfTestTest {

    private final static TestMidiDeviceProvider.TestMidiDevice INTERFACE = TestMidiDeviceProvider.INTERFACE;
    private final static String NAME = INTERFACE.getDeviceInfo().getName();

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final DefaultMidiService midiService = new DefaultMidiService();
    private final ActionMidiExecutionService actionMidiExecutionService = mock(ActionMidiExecutionService.class);
    private final MidiRouter midiRouter = mock(MidiRouter.class);
    private DefaultMidiDeviceInService inService;
    private MidiSelfTest midiSelfTest;

    @BeforeEach
    void setUp() {
        INTERFACE.reset();
        when(settingsService.getSettings()).thenReturn(settings);
    }

    @AfterEach
    void tearDown() {
        INTERFACE.reset();
    }

    private MidiDevice interfaceOfTheSettings(MidiDirection midiDirection) throws Exception {
        return midiService.getMidiDevices(midiDirection).stream()
                .filter(midiDevice -> midiDevice.getName().equals(NAME))
                .findFirst().orElseThrow();
    }

    // A unit with the interface as MIDI in and out device of the settings
    private void startUnit(boolean midiOut, boolean midiIn) throws Exception {
        if (midiOut) {
            settings.setMidiOutDevice(interfaceOfTheSettings(MidiDirection.OUT));
        }
        if (midiIn) {
            settings.setMidiInDevice(interfaceOfTheSettings(MidiDirection.IN));
        }

        DefaultMidiDeviceOutService outService = new DefaultMidiDeviceOutService(settingsService, midiService);
        outService.init();

        MidiRouterFactory midiRouterFactory = mock(MidiRouterFactory.class);
        when(midiRouterFactory.getSettingsMidiRouter(any())).thenReturn(midiRouter);
        inService = new DefaultMidiDeviceInService(settingsService, actionMidiExecutionService, midiService, midiRouterFactory);
        inService.init();

        midiSelfTest = new MidiSelfTest(settingsService, outService, inService);
    }

    private SelfTestContext runSelfTest() throws Exception {
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> false);
        midiSelfTest.prepare();
        try {
            midiSelfTest.run(context);
        } finally {
            midiSelfTest.restore();
        }
        return context;
    }

    private static SelfTestCheck check(SelfTestContext context, String name) {
        return context.getChecks().stream().filter(check -> check.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everyMessageComesBackThroughALoopbackCable() throws Exception {
        startUnit(true, true);
        INTERFACE.loopbackCable = message -> message;

        SelfTestContext context = runSelfTest();

        SelfTestCheck midiOut = check(context, MidiSelfTest.MIDI_OUT);
        assertEquals(SelfTestOutcome.PASSED, midiOut.getResult());
        assertEquals(NAME, midiOut.getTarget());
        assertEquals("200 messages sent", midiOut.getDetails());

        SelfTestCheck loopback = check(context, MidiSelfTest.MIDI_LOOPBACK);
        assertEquals(SelfTestOutcome.PASSED, loopback.getResult(), loopback.getDetails());
        assertEquals("MIDI out " + NAME + " → MIDI in " + NAME, loopback.getTarget());
        assertEquals("200 of 200 messages received", loopback.getDetails());

        // The burst coming back started no actions and wasn't routed
        verify(actionMidiExecutionService, never()).processMidiSignal(any());
        verify(midiRouter, never()).sendSignal(any(), any());
    }

    @Test
    void theLoopbackIsSkippedWithoutCable() throws Exception {
        startUnit(true, true);

        SelfTestContext context = runSelfTest();

        assertEquals(SelfTestOutcome.PASSED, check(context, MidiSelfTest.MIDI_OUT).getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(context, MidiSelfTest.MIDI_LOOPBACK).getResult());
        assertEquals("Nothing received, no loopback cable", check(context, MidiSelfTest.MIDI_LOOPBACK).getDetails());
        // Sent to the device: the burst and all notes off on every channel
        assertEquals(200 + 16, INTERFACE.received.size());
    }

    @Test
    void failsWhenMessagesGetLost() throws Exception {
        startUnit(true, true);
        AtomicInteger count = new AtomicInteger();
        INTERFACE.loopbackCable = message -> count.incrementAndGet() % 50 == 0 ? null : message;

        SelfTestCheck loopback = check(runSelfTest(), MidiSelfTest.MIDI_LOOPBACK);

        assertEquals(SelfTestOutcome.FAILED, loopback.getResult());
        assertEquals("196 of 200 messages received", loopback.getDetails());
    }

    @Test
    void failsWhenMessagesChange() throws Exception {
        startUnit(true, true);
        INTERFACE.loopbackCable = message -> {
            if (message instanceof ShortMessage shortMessage && shortMessage.getCommand() == ShortMessage.NOTE_OFF) {
                // A note off as note on with velocity 0
                try {
                    return new ShortMessage(ShortMessage.NOTE_ON, shortMessage.getChannel(), shortMessage.getData1(), 0);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return message;
        };

        SelfTestCheck loopback = check(runSelfTest(), MidiSelfTest.MIDI_LOOPBACK);

        assertEquals(SelfTestOutcome.FAILED, loopback.getResult());
        assertEquals("150 of 200 messages received, 50 other messages (changed, twice or out of order)", loopback.getDetails());
    }

    @Test
    void ignoresTheRealTimeMessagesOfTheInterface() throws Exception {
        startUnit(true, true);
        INTERFACE.loopbackCable = message -> {
            // Active sensing, which some interfaces send by themselves
            INTERFACE.play(shortMessage(ShortMessage.ACTIVE_SENSING));
            return message;
        };

        assertEquals(SelfTestOutcome.PASSED, check(runSelfTest(), MidiSelfTest.MIDI_LOOPBACK).getResult());
    }

    private static ShortMessage shortMessage(int status) {
        try {
            return new ShortMessage(status);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theInputStartsNoActionsAndIsNotRoutedDuringTheSelfTest() throws Exception {
        startUnit(true, true);
        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100);

        midiSelfTest.prepare();
        INTERFACE.play(noteOn);
        verify(actionMidiExecutionService, never()).processMidiSignal(any());
        verify(midiRouter, never()).sendSignal(any(), any());

        midiSelfTest.restore();
        INTERFACE.play(noteOn);
        verify(actionMidiExecutionService).processMidiSignal(noteOn);
        verify(midiRouter).sendSignal(any(), any());
    }

    @Test
    void turnsAllNotesOffAtTheEnd() throws Exception {
        startUnit(true, true);
        INTERFACE.loopbackCable = message -> message;

        runSelfTest();

        List<MidiMessage> last = INTERFACE.received.subList(INTERFACE.received.size() - 16, INTERFACE.received.size());
        for (int channel = 0; channel < 16; channel++) {
            ShortMessage shortMessage = (ShortMessage) last.get(channel);
            assertEquals(ShortMessage.CONTROL_CHANGE, shortMessage.getCommand());
            assertEquals(channel, shortMessage.getChannel());
            assertEquals(123, shortMessage.getData1());
        }
    }

    @Test
    void isSkippedWithoutMidiOutDevice() throws Exception {
        startUnit(false, true);

        SelfTestContext context = runSelfTest();

        assertEquals(SelfTestOutcome.SKIPPED, check(context, MidiSelfTest.MIDI_OUT).getResult());
        assertEquals("No MIDI out device in the settings", check(context, MidiSelfTest.MIDI_OUT).getDetails());
        assertEquals(SelfTestOutcome.SKIPPED, check(context, MidiSelfTest.MIDI_LOOPBACK).getResult());
        assertTrue(INTERFACE.received.isEmpty());
    }

    @Test
    void theLoopbackIsSkippedWithoutMidiInDevice() throws Exception {
        startUnit(true, false);
        INTERFACE.loopbackCable = message -> message;

        SelfTestContext context = runSelfTest();

        assertEquals(SelfTestOutcome.PASSED, check(context, MidiSelfTest.MIDI_OUT).getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(context, MidiSelfTest.MIDI_LOOPBACK).getResult());
        assertEquals("No MIDI in device in the settings", check(context, MidiSelfTest.MIDI_LOOPBACK).getDetails());
    }

    @Test
    void stopsWhenACompositionStarts() throws Exception {
        startUnit(true, true);
        INTERFACE.loopbackCable = message -> message;
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> INTERFACE.received.size() >= 20);

        midiSelfTest.prepare();
        midiSelfTest.run(context);
        midiSelfTest.restore();

        assertEquals(SelfTestOutcome.SKIPPED, check(context, MidiSelfTest.MIDI_OUT).getResult());
        assertEquals(SelfTestContext.STOPPED, check(context, MidiSelfTest.MIDI_LOOPBACK).getDetails());
        // Not the whole burst, but all notes off
        assertEquals(20 + 16, INTERFACE.received.size());
    }

    @Test
    void theBurstHasNotesControllersAndProgramsOnEveryChannel() {
        List<ShortMessage> burst = MidiSelfTest.burst();

        assertEquals(200, burst.size());
        assertEquals(16, burst.stream().map(ShortMessage::getChannel).distinct().count());
        assertEquals(List.of(ShortMessage.NOTE_ON, ShortMessage.CONTROL_CHANGE, ShortMessage.PROGRAM_CHANGE, ShortMessage.NOTE_OFF),
                burst.stream().map(ShortMessage::getCommand).distinct().toList());
        // A note on with velocity 0 would be a note off
        assertTrue(burst.stream().noneMatch(message -> message.getCommand() == ShortMessage.NOTE_ON && message.getData2() == 0));
        // Only undefined controllers
        assertTrue(burst.stream().filter(message -> message.getCommand() == ShortMessage.CONTROL_CHANGE)
                .allMatch(message -> message.getData1() >= 20 && message.getData1() <= 31));
    }

    @Test
    void messagesOutOfOrderFail() {
        List<ShortMessage> sent = MidiSelfTest.burst().subList(0, 4);
        List<ShortMessage> received = List.of(sent.get(0), sent.get(2), sent.get(1), sent.get(3));

        SelfTestCheck check = MidiSelfTest.compare(sent, received, "loopback");

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("3 of 4 messages received, 1 other messages (changed, twice or out of order)", check.getDetails(),
                received.stream().map(message -> String.valueOf(message.getStatus())).collect(Collectors.joining(",")));
    }

}
