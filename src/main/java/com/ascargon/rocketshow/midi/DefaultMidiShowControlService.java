package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.SysexMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DefaultMidiShowControlService implements MidiShowControlService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultMidiShowControlService.class);

    private final static int UNIVERSAL_REALTIME_SYSEX = 0x7F;
    private final static int ALL_CALL_DEVICE_ID = 0x7F;

    private final static int SUB_ID_SHOW_CONTROL = 0x02;
    private final static int SUB_ID_MACHINE_CONTROL_COMMAND = 0x06;

    // MIDI Show Control commands
    private final static int MSC_GO = 0x01;
    private final static int MSC_STOP = 0x02;
    private final static int MSC_RESUME = 0x03;
    private final static int MSC_LOAD = 0x05;
    private final static int MSC_ALL_OFF = 0x08;
    private final static int MSC_RESET = 0x0A;
    private final static int MSC_GO_OFF = 0x0B;

    // MIDI Machine Control commands
    private final static int MMC_STOP = 0x01;
    private final static int MMC_PLAY = 0x02;
    private final static int MMC_DEFERRED_PLAY = 0x03;
    private final static int MMC_PAUSE = 0x09;
    private final static int MMC_LOCATE = 0x44;

    // MMC locate sub-command that carries a target position
    private final static int MMC_LOCATE_TARGET = 0x01;

    private final SettingsService settingsService;
    private final MidiCompositionSelectionService midiCompositionSelectionService;
    private final MidiTimecodeSlaveService midiTimecodeSlaveService;

    // Lazy to avoid a circular dependency: the player service is built on top of the MIDI services.
    private final PlayerService playerService;

    // Transport commands load and preroll, which must not block the MIDI input thread
    private final ExecutorService transportExecutor = Executors.newSingleThreadExecutor();

    public DefaultMidiShowControlService(SettingsService settingsService,
                                         MidiCompositionSelectionService midiCompositionSelectionService,
                                         MidiTimecodeSlaveService midiTimecodeSlaveService,
                                         @Lazy PlayerService playerService) {
        this.settingsService = settingsService;
        this.midiCompositionSelectionService = midiCompositionSelectionService;
        this.midiTimecodeSlaveService = midiTimecodeSlaveService;
        this.playerService = playerService;
    }

    @Override
    public void processMidiMessage(MidiMessage midiMessage) {
        if (!(midiMessage instanceof SysexMessage sysexMessage)) {
            return;
        }

        byte[] data = sysexMessage.getMessage();

        // F0 7F <deviceID> <subID> ... F7
        if (data == null || data.length < 6 || (data[1] & 0xFF) != UNIVERSAL_REALTIME_SYSEX) {
            return;
        }

        Settings settings = settingsService.getSettings();
        int deviceId = data[2] & 0xFF;
        int subId = data[3] & 0xFF;

        if (subId == SUB_ID_SHOW_CONTROL && Boolean.TRUE.equals(settings.getMidiShowControlEnabled())
                && isAddressedToThisDevice(deviceId, settings.getMidiShowControlDeviceId())) {

            processShowControl(data);
        } else if (subId == SUB_ID_MACHINE_CONTROL_COMMAND && Boolean.TRUE.equals(settings.getMidiMachineControlEnabled())
                && isAddressedToThisDevice(deviceId, settings.getMidiMachineControlDeviceId())) {

            processMachineControl(data);
        }
    }

    /**
     * Device IDs 0-126 address a single device, 127 is the all-call every device listens to. Group
     * IDs (112-126) are not supported and only reach the device configured with that exact ID.
     */
    private static boolean isAddressedToThisDevice(int deviceId, Integer configuredDeviceId) {
        return deviceId == ALL_CALL_DEVICE_ID
                || (configuredDeviceId != null && deviceId == configuredDeviceId);
    }

    // F0 7F <deviceID> 02 <commandFormat> <command> [<cue> 00 <list> 00 <path>] F7
    private void processShowControl(byte[] data) {
        if (data.length < 7) {
            return;
        }

        // The command format (lighting, sound, video, all-types) is deliberately not filtered: in
        // MSC it is the device ID that says who a command is for.
        int command = data[5] & 0xFF;
        String cue = readCueNumber(data);

        logger.debug("Received MIDI Show Control command {} for cue '{}'", command, cue);

        switch (command) {
            case MSC_GO -> {
                if (cue.isEmpty()) {
                    // A GO without a cue number means "run what is loaded"
                    submitTransport(playerService::play);
                } else if (!midiCompositionSelectionService.selectByShowControlCue(cue, true)) {
                    logger.info("No composition is configured for MIDI Show Control cue '{}'", cue);
                }
            }
            case MSC_LOAD -> {
                if (!midiCompositionSelectionService.selectByShowControlCue(cue, false)) {
                    logger.info("No composition is configured for MIDI Show Control cue '{}'", cue);
                }
            }
            case MSC_STOP -> submitTransport(playerService::pause);
            case MSC_RESUME -> submitTransport(playerService::play);
            case MSC_ALL_OFF, MSC_RESET, MSC_GO_OFF -> submitTransport(playerService::stop);
            default -> logger.debug("Ignoring unsupported MIDI Show Control command {}", command);
        }
    }

    /**
     * The cue number is the first of up to three null-terminated ASCII fields (cue, cue list, cue
     * path) that follow the command. All three are optional.
     */
    private static String readCueNumber(byte[] data) {
        StringBuilder cue = new StringBuilder();

        // Everything from after the command byte up to the field separator or the end of the SysEx
        for (int index = 6; index < data.length - 1; index++) {
            int value = data[index] & 0xFF;

            if (value == 0x00) {
                break;
            }

            cue.append((char) value);
        }

        return cue.toString().trim();
    }

    // F0 7F <deviceID> 06 <command> [...] F7
    private void processMachineControl(byte[] data) {
        int command = data[4] & 0xFF;

        logger.debug("Received MIDI Machine Control command {}", command);

        if (midiTimecodeSlaveService.isLocked()) {
            // An incoming timecode already owns the transport; letting MMC seek or stop as well
            // would only fight it
            logger.debug("Ignoring the MIDI Machine Control command while following a MIDI timecode");
            return;
        }

        switch (command) {
            case MMC_PLAY, MMC_DEFERRED_PLAY -> submitTransport(playerService::play);
            case MMC_STOP -> submitTransport(playerService::stop);
            case MMC_PAUSE -> submitTransport(playerService::pause);
            case MMC_LOCATE -> processMachineControlLocate(data);
            default -> logger.debug("Ignoring unsupported MIDI Machine Control command {}", command);
        }
    }

    // F0 7F <deviceID> 06 44 <byteCount> 01 <hr> <mn> <sc> <fr> <ff> F7
    private void processMachineControlLocate(byte[] data) {
        if (data.length < 12 || (data[6] & 0xFF) != MMC_LOCATE_TARGET) {
            // Locate by information field (sub-command 00) carries no position we could use
            return;
        }

        int hourAndRate = data[7] & 0xFF;
        MidiTimecodeFrameRate frameRate = MidiTimecodeFrameRate.fromMidiRateCode((hourAndRate >> 5) & 0x03);
        MidiTimecodePosition position = new MidiTimecodePosition(
                hourAndRate & 0x1F,
                data[8] & 0x3F,
                data[9] & 0x3F,
                data[10] & 0x1F);

        long positionMillis = position.toMillis(frameRate);

        logger.debug("Locating to {}ms from MIDI Machine Control", positionMillis);

        submitTransport(() -> playerService.seek(positionMillis));
    }

    private interface TransportCommand {
        void run() throws Exception;
    }

    private void submitTransport(TransportCommand command) {
        transportExecutor.execute(() -> {
            try {
                command.run();
            } catch (Exception e) {
                logger.error("Could not execute a transport command received over MIDI", e);
            }
        });
    }

    @PreDestroy
    public void close() {
        transportExecutor.shutdownNow();
    }

}
