package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sound.midi.MidiMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSystemTestServiceTest {

    private SettingsService settingsService;
    private Settings settings;
    private CompositionService compositionService;
    private PlayerService playerService;
    private MidiDeviceInService midiDeviceInService;
    private MidiDeviceOutService midiDeviceOutService;
    private RaspberryGpioOutService raspberryGpioOutService;

    private TestableSystemTestService systemTestService;

    /**
     * A subclass that removes the timing/hardware seams so the orchestration can be tested
     * deterministically: sleeps are skipped and the MIDI loopback capture is stubbed.
     */
    private static class TestableSystemTestService extends DefaultSystemTestService {

        boolean midiReturnNull = false;
        boolean midiEcho = false;
        List<byte[]> midiCaptureResult = new ArrayList<>();
        List<MidiMessage> capturedSentMessages;

        CompositionPlayer.PlayState testPlayState = CompositionPlayer.PlayState.PLAYING;
        boolean throwOnPlayStateCheck = false;

        TestableSystemTestService(SettingsService settingsService, CompositionService compositionService,
                                  PlayerService playerService, MidiDeviceInService midiDeviceInService,
                                  MidiDeviceOutService midiDeviceOutService,
                                  RaspberryGpioOutService raspberryGpioOutService) {
            super(settingsService, compositionService, playerService, midiDeviceInService,
                    midiDeviceOutService, raspberryGpioOutService);
        }

        @Override
        protected void sleep(long millis) {
            // no-op to keep the tests fast and deterministic
        }

        @Override
        protected CompositionPlayer.PlayState getTestCompositionPlayState() {
            if (throwOnPlayStateCheck) {
                throw new RuntimeException("boom");
            }
            return testPlayState;
        }

        @Override
        protected List<byte[]> sendAndCaptureMidi(List<MidiMessage> messages) {
            capturedSentMessages = messages;

            if (midiReturnNull) {
                return null;
            }

            if (midiEcho) {
                List<byte[]> echoed = new ArrayList<>();
                for (MidiMessage message : messages) {
                    echoed.add(message.getMessage());
                }
                return echoed;
            }

            return midiCaptureResult;
        }
    }

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        settings = mock(Settings.class);
        compositionService = mock(CompositionService.class);
        playerService = mock(PlayerService.class);
        midiDeviceInService = mock(MidiDeviceInService.class);
        midiDeviceOutService = mock(MidiDeviceOutService.class);
        raspberryGpioOutService = mock(RaspberryGpioOutService.class);

        when(settingsService.getSettings()).thenReturn(settings);

        // Sensible defaults: nothing configured, no hardware
        when(settings.getDefaultComposition()).thenReturn(null);
        when(settings.getEnableRaspberryGpio()).thenReturn(false);
        when(settings.getRaspberryGpioOutputPinBcmList()).thenReturn(new ArrayList<>());
        when(midiDeviceOutService.isConnected()).thenReturn(false);

        systemTestService = new TestableSystemTestService(settingsService, compositionService, playerService,
                midiDeviceInService, midiDeviceOutService, raspberryGpioOutService);
    }

    // --- Default composition --------------------------------------------------------------

    @Test
    void defaultCompositionSkippedWhenNotConfigured() {
        when(settings.getDefaultComposition()).thenReturn("");

        SystemTestStep step = systemTestService.testDefaultComposition();

        assertTrue(step.isSkipped());
        assertTrue(step.isSuccess());
    }

    @Test
    void defaultCompositionFailsWhenNotFound() {
        when(settings.getDefaultComposition()).thenReturn("missing");
        when(compositionService.getComposition("missing")).thenReturn(null);

        SystemTestStep step = systemTestService.testDefaultComposition();

        assertFalse(step.isSkipped());
        assertFalse(step.isSuccess());
    }

    @Test
    void defaultCompositionPassesWhenPlaying() throws Exception {
        when(settings.getDefaultComposition()).thenReturn("demo");
        when(compositionService.getComposition("demo")).thenReturn(mock(Composition.class));
        systemTestService.testPlayState = CompositionPlayer.PlayState.PLAYING;

        SystemTestStep step = systemTestService.testDefaultComposition();

        assertTrue(step.isSuccess());
        assertFalse(step.isSkipped());
        verify(playerService).playTestComposition(any());
        verify(playerService).stopTestComposition();
    }

    @Test
    void defaultCompositionFailsWhenNotReachingPlayingState() {
        when(settings.getDefaultComposition()).thenReturn("demo");
        when(compositionService.getComposition("demo")).thenReturn(mock(Composition.class));
        systemTestService.testPlayState = CompositionPlayer.PlayState.STOPPED;

        SystemTestStep step = systemTestService.testDefaultComposition();

        assertFalse(step.isSuccess());
    }

    @Test
    void defaultCompositionStopsEvenWhenStateCheckThrows() throws Exception {
        when(settings.getDefaultComposition()).thenReturn("demo");
        when(compositionService.getComposition("demo")).thenReturn(mock(Composition.class));
        systemTestService.throwOnPlayStateCheck = true;

        SystemTestStep step = systemTestService.testDefaultComposition();

        assertFalse(step.isSuccess());
        // The composition was started, so it must be stopped again even after the error
        verify(playerService).stopTestComposition();
    }

    // --- GPIO -----------------------------------------------------------------------------

    @Test
    void gpioSkippedWhenDisabled() {
        when(settings.getEnableRaspberryGpio()).thenReturn(false);

        SystemTestStep step = systemTestService.testGpioOutputs();

        assertTrue(step.isSkipped());
        verify(raspberryGpioOutService, never()).executeAction(any());
    }

    @Test
    void gpioSkippedWhenNoPinsConfigured() {
        when(settings.getEnableRaspberryGpio()).thenReturn(true);
        when(settings.getRaspberryGpioOutputPinBcmList()).thenReturn(new ArrayList<>());

        SystemTestStep step = systemTestService.testGpioOutputs();

        assertTrue(step.isSkipped());
        verify(raspberryGpioOutService, never()).executeAction(any());
    }

    @Test
    void gpioTogglesEachConfiguredPinHighThenLow() {
        when(settings.getEnableRaspberryGpio()).thenReturn(true);
        when(settings.getRaspberryGpioOutputPinBcmList()).thenReturn(List.of(17, 27));

        SystemTestStep step = systemTestService.testGpioOutputs();

        assertTrue(step.isSuccess());
        assertFalse(step.isSkipped());

        ArgumentCaptor<ActionRaspberryGpio> captor = ArgumentCaptor.forClass(ActionRaspberryGpio.class);
        // 2 pins x (high + low) = 4 actions
        verify(raspberryGpioOutService, times(4)).executeAction(captor.capture());

        List<ActionRaspberryGpio> actions = captor.getAllValues();
        assertEquals(17, actions.get(0).getPinId());
        assertTrue(actions.get(0).getHigh());
        assertEquals(17, actions.get(1).getPinId());
        assertFalse(actions.get(1).getHigh());
        assertEquals(27, actions.get(2).getPinId());
        assertTrue(actions.get(2).getHigh());
        assertEquals(27, actions.get(3).getPinId());
        assertFalse(actions.get(3).getHigh());
    }

    // --- MIDI -----------------------------------------------------------------------------

    @Test
    void midiSkippedWhenOutNotConnected() {
        when(midiDeviceOutService.isConnected()).thenReturn(false);

        SystemTestStep step = systemTestService.testMidiLoopback();

        assertTrue(step.isSkipped());
    }

    @Test
    void midiSkippedWhenNoInDevice() {
        when(midiDeviceOutService.isConnected()).thenReturn(true);
        systemTestService.midiReturnNull = true;

        SystemTestStep step = systemTestService.testMidiLoopback();

        assertTrue(step.isSkipped());
    }

    @Test
    void midiPassesWhenLoopbackEchoesMessages() {
        when(midiDeviceOutService.isConnected()).thenReturn(true);
        systemTestService.midiEcho = true;

        SystemTestStep step = systemTestService.testMidiLoopback();

        assertTrue(step.isSuccess());
        assertFalse(step.isSkipped());
        assertFalse(systemTestService.capturedSentMessages.isEmpty());
    }

    @Test
    void midiFailsWhenNothingReceived() {
        when(midiDeviceOutService.isConnected()).thenReturn(true);
        systemTestService.midiCaptureResult = new ArrayList<>();

        SystemTestStep step = systemTestService.testMidiLoopback();

        assertFalse(step.isSuccess());
        assertFalse(step.isSkipped());
    }

    // --- Aggregation ----------------------------------------------------------------------

    @Test
    void runSystemTestSucceedsWhenAllStepsPassOrSkip() throws Exception {
        when(settings.getDefaultComposition()).thenReturn("demo");
        when(compositionService.getComposition("demo")).thenReturn(mock(Composition.class));
        systemTestService.testPlayState = CompositionPlayer.PlayState.PLAYING;
        when(midiDeviceOutService.isConnected()).thenReturn(true);
        systemTestService.midiEcho = true;

        SystemTestResult result = systemTestService.runSystemTest();

        assertTrue(result.isSuccess());
        assertEquals(3, result.getSteps().size());
    }

    @Test
    void runSystemTestFailsWhenAStepFails() {
        // Default composition configured but missing -> failing step
        when(settings.getDefaultComposition()).thenReturn("missing");
        when(compositionService.getComposition("missing")).thenReturn(null);

        SystemTestResult result = systemTestService.runSystemTest();

        assertFalse(result.isSuccess());
        assertEquals(3, result.getSteps().size());
    }

    @Test
    void runSystemTestSucceedsWhenEverythingIsSkipped() {
        // All defaults: nothing configured/connected -> all steps skipped
        SystemTestResult result = systemTestService.runSystemTest();

        assertTrue(result.isSuccess());
        assertTrue(result.getSteps().stream().allMatch(SystemTestStep::isSkipped));
    }

    // --- MIDI message matching helper -----------------------------------------------------

    @Test
    void midiMessagesMatchReturnsTrueWhenAllExpectedPresent() {
        List<byte[]> expected = List.of(new byte[]{0x10, 0x20}, new byte[]{0x30});
        List<byte[]> received = List.of(new byte[]{0x30}, new byte[]{0x10, 0x20});

        assertTrue(DefaultSystemTestService.midiMessagesMatch(expected, received));
    }

    @Test
    void midiMessagesMatchToleratesExtraReceivedMessages() {
        List<byte[]> expected = List.of(new byte[]{0x10});
        List<byte[]> received = List.of(new byte[]{0x10}, new byte[]{(byte) 0xFE});

        assertTrue(DefaultSystemTestService.midiMessagesMatch(expected, received));
    }

    @Test
    void midiMessagesMatchReturnsFalseWhenExpectedMissing() {
        List<byte[]> expected = List.of(new byte[]{0x10}, new byte[]{0x40});
        List<byte[]> received = List.of(new byte[]{0x10});

        assertFalse(DefaultSystemTestService.midiMessagesMatch(expected, received));
    }
}
