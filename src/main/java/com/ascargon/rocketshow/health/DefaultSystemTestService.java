package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.midi.MidiDeviceInService;
import com.ascargon.rocketshow.midi.MidiDeviceOutService;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.raspberry.ActionRaspberryGpio;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class DefaultSystemTestService implements SystemTestService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultSystemTestService.class);

    // How long to let the default composition play before checking its play state
    private static final long COMPOSITION_PLAY_MILLIS = 2000;
    // How long to hold each GPIO output state before switching it
    private static final long GPIO_TOGGLE_MILLIS = 500;
    // How long to wait for the MIDI loopback messages to arrive
    private static final long MIDI_LOOPBACK_TIMEOUT_MILLIS = 1000;

    // The notes sent during the MIDI loopback test (C4, E4, G4)
    private static final int[] MIDI_TEST_NOTES = {60, 64, 67};

    private final SettingsService settingsService;
    private final CompositionService compositionService;
    private final PlayerService playerService;
    private final MidiDeviceInService midiDeviceInService;
    private final MidiDeviceOutService midiDeviceOutService;
    private final RaspberryGpioOutService raspberryGpioOutService;

    public DefaultSystemTestService(
            SettingsService settingsService,
            CompositionService compositionService,
            PlayerService playerService,
            MidiDeviceInService midiDeviceInService,
            MidiDeviceOutService midiDeviceOutService,
            RaspberryGpioOutService raspberryGpioOutService
    ) {
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.playerService = playerService;
        this.midiDeviceInService = midiDeviceInService;
        this.midiDeviceOutService = midiDeviceOutService;
        this.raspberryGpioOutService = raspberryGpioOutService;
    }

    @Override
    public SystemTestResult runSystemTest() {
        logger.info("Running system self-test");

        SystemTestResult result = new SystemTestResult();
        result.addStep(testDefaultComposition());
        result.addStep(testGpioOutputs());
        result.addStep(testMidiLoopback());

        logger.info("System self-test finished. Success: {}", result.isSuccess());

        return result;
    }

    // Play the configured default composition and make sure it actually starts playing
    // (i.e. the audio/video/lighting pipeline could be set up without errors).
    SystemTestStep testDefaultComposition() {
        String name = "Default composition playback";

        String defaultCompositionName = settingsService.getSettings().getDefaultComposition();

        if (defaultCompositionName == null || defaultCompositionName.isEmpty()) {
            return skipped(name, "No default composition configured");
        }

        Composition composition = compositionService.getComposition(defaultCompositionName);

        if (composition == null) {
            return failed(name, "Default composition '" + defaultCompositionName + "' not found");
        }

        boolean started = false;

        try {
            playerService.playTestComposition(composition);
            started = true;

            sleep(COMPOSITION_PLAY_MILLIS);

            CompositionPlayer.PlayState playState = getTestCompositionPlayState();

            if (playState == CompositionPlayer.PlayState.PLAYING) {
                return passed(name, "Played default composition '" + defaultCompositionName + "'");
            }

            return failed(name, "Default composition did not reach the playing state (was " + playState + ")");
        } catch (Exception e) {
            logger.error("System test: default composition playback failed", e);
            return failed(name, "Error while playing the default composition: " + e.getMessage());
        } finally {
            if (started) {
                try {
                    playerService.stopTestComposition();
                } catch (Exception e) {
                    logger.error("System test: could not stop the default composition again", e);
                }
            }
        }
    }

    // Toggle every configured GPIO output pin high and back to low so it can be verified
    // (e.g. with an LED or a loopback wire to an input).
    SystemTestStep testGpioOutputs() {
        String name = "GPIO outputs";

        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return skipped(name, "GPIO is disabled in the settings");
        }

        List<Integer> pins = settingsService.getSettings().getRaspberryGpioOutputPinBcmList();

        if (pins == null || pins.isEmpty()) {
            return skipped(name, "No GPIO output pins configured");
        }

        try {
            for (Integer pin : pins) {
                setGpio(pin, true);
                sleep(GPIO_TOGGLE_MILLIS);
                setGpio(pin, false);
            }

            return passed(name, "Toggled " + pins.size() + " GPIO output pin(s)");
        } catch (Exception e) {
            logger.error("System test: GPIO output test failed", e);
            return failed(name, "Error while toggling the GPIO outputs: " + e.getMessage());
        }
    }

    // Send a few MIDI messages to the MIDI out device and verify that the same messages
    // arrive on the MIDI in device (requires a MIDI loopback cable).
    SystemTestStep testMidiLoopback() {
        String name = "MIDI loopback";

        if (!midiDeviceOutService.isConnected()) {
            return skipped(name, "No MIDI out device connected");
        }

        try {
            List<MidiMessage> messages = buildTestMidiMessages();
            List<byte[]> received = sendAndCaptureMidi(messages);

            if (received == null) {
                return skipped(name, "No MIDI in device available to verify the loopback");
            }

            if (midiMessagesMatch(toByteList(messages), received)) {
                return passed(name, "Sent and received " + messages.size() + " MIDI message(s)");
            }

            return failed(name, "MIDI loopback mismatch. Sent " + messages.size()
                    + " message(s), received " + received.size());
        } catch (Exception e) {
            logger.error("System test: MIDI loopback test failed", e);
            return failed(name, "Error during the MIDI loopback test: " + e.getMessage());
        }
    }

    private List<MidiMessage> buildTestMidiMessages() throws Exception {
        List<MidiMessage> messages = new ArrayList<>();

        for (int note : MIDI_TEST_NOTES) {
            messages.add(new ShortMessage(ShortMessage.NOTE_ON, 0, note, 100));
        }

        return messages;
    }

    // Send the given messages to the MIDI out device and capture everything that arrives
    // on the MIDI in device until all messages are received or a timeout elapses.
    // Returns null if there is no (non-serial) MIDI in device to capture from.
    //
    // Extracted as a protected method so the hardware interaction can be stubbed in tests.
    protected List<byte[]> sendAndCaptureMidi(List<MidiMessage> messages) throws Exception {
        MidiDevice inDevice = midiDeviceInService.getMidiDevice();

        if (inDevice == null) {
            return null;
        }

        List<byte[]> received = Collections.synchronizedList(new ArrayList<>());

        // getTransmitter() returns a new transmitter on every call, so capturing here does
        // not interfere with the regular MIDI in routing.
        Transmitter transmitter = inDevice.getTransmitter();
        transmitter.setReceiver(new Receiver() {
            @Override
            public void send(MidiMessage message, long timeStamp) {
                received.add(message.getMessage());
            }

            @Override
            public void close() {
                // nothing to do
            }
        });

        try {
            for (MidiMessage message : messages) {
                midiDeviceOutService.sendMessage(message);
            }

            long deadline = System.currentTimeMillis() + MIDI_LOOPBACK_TIMEOUT_MILLIS;

            while (received.size() < messages.size() && System.currentTimeMillis() < deadline) {
                sleep(20);
            }
        } finally {
            transmitter.close();
        }

        return new ArrayList<>(received);
    }

    // Extracted as a protected method so the play state can be stubbed in tests without
    // having to mock the CompositionPlayer.
    protected CompositionPlayer.PlayState getTestCompositionPlayState() {
        return playerService.getTestCompositionPlayer().getPlayState();
    }

    private void setGpio(int pinId, boolean high) {
        ActionRaspberryGpio action = new ActionRaspberryGpio();
        action.setPinId(pinId);
        action.setHigh(high);
        raspberryGpioOutService.executeAction(action);
    }

    // Every expected message must be present in the received messages (order independent,
    // extra received messages like active sensing are tolerated).
    static boolean midiMessagesMatch(List<byte[]> expected, List<byte[]> received) {
        for (byte[] expectedMessage : expected) {
            boolean found = received.stream().anyMatch(actual -> Arrays.equals(actual, expectedMessage));

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private static List<byte[]> toByteList(List<MidiMessage> messages) {
        List<byte[]> list = new ArrayList<>();

        for (MidiMessage message : messages) {
            list.add(message.getMessage());
        }

        return list;
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static SystemTestStep passed(String name, String message) {
        return new SystemTestStep(name, true, message);
    }

    private static SystemTestStep failed(String name, String message) {
        return new SystemTestStep(name, false, message);
    }

    private static SystemTestStep skipped(String name, String message) {
        SystemTestStep step = new SystemTestStep(name, true, message);
        step.setSkipped(true);
        return step;
    }

}
