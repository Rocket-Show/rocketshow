package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.SysexMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMidiShowControlServiceTest {

    private final static long VERIFY_TIMEOUT_MILLIS = 2_000;

    private final static int DEVICE_ID = 3;

    private Settings settings;
    private MidiCompositionSelectionService midiCompositionSelectionService;
    private MidiTimecodeSlaveService midiTimecodeSlaveService;
    private PlayerService playerService;
    private DefaultMidiShowControlService service;

    @BeforeEach
    void setUp() {
        settings = new Settings();
        settings.setMidiShowControlEnabled(true);
        settings.setMidiShowControlDeviceId(DEVICE_ID);
        settings.setMidiMachineControlEnabled(true);
        settings.setMidiMachineControlDeviceId(DEVICE_ID);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        midiCompositionSelectionService = mock(MidiCompositionSelectionService.class);
        when(midiCompositionSelectionService.selectByShowControlCue(anyString(), anyBoolean())).thenReturn(true);

        midiTimecodeSlaveService = mock(MidiTimecodeSlaveService.class);
        playerService = mock(PlayerService.class);

        service = new DefaultMidiShowControlService(settingsService, midiCompositionSelectionService,
                midiTimecodeSlaveService, playerService);
    }

    /**
     * F0 7F &lt;deviceID&gt; 02 &lt;commandFormat&gt; &lt;command&gt; [&lt;cue&gt;] F7
     */
    private static SysexMessage showControl(int deviceId, int command, String cue) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0xF0);
        data.write(0x7F);
        data.write(deviceId);
        data.write(0x02);
        // Command format "all types"
        data.write(0x7F);
        data.write(command);
        data.writeBytes(cue.getBytes(StandardCharsets.US_ASCII));
        data.write(0xF7);

        return sysex(data);
    }

    /**
     * F0 7F &lt;deviceID&gt; 06 &lt;command&gt; [...] F7
     */
    private static SysexMessage machineControl(int deviceId, int... command) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0xF0);
        data.write(0x7F);
        data.write(deviceId);
        data.write(0x06);

        for (int commandByte : command) {
            data.write(commandByte);
        }

        data.write(0xF7);

        return sysex(data);
    }

    private static SysexMessage sysex(ByteArrayOutputStream data) throws Exception {
        SysexMessage message = new SysexMessage();
        message.setMessage(data.toByteArray(), data.size());
        return message;
    }

    @Test
    void goWithACueSelectsAndPlaysThatComposition() throws Exception {
        service.processMidiMessage(showControl(DEVICE_ID, 0x01, "12.5"));

        verify(midiCompositionSelectionService).selectByShowControlCue("12.5", true);
    }

    @Test
    void loadSelectsWithoutPlaying() throws Exception {
        service.processMidiMessage(showControl(DEVICE_ID, 0x05, "12.5"));

        verify(midiCompositionSelectionService).selectByShowControlCue("12.5", false);
    }

    @Test
    void goWithoutACueRunsWhatIsLoaded() throws Exception {
        service.processMidiMessage(showControl(DEVICE_ID, 0x01, ""));

        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).play();
        verify(midiCompositionSelectionService, never()).selectByShowControlCue(anyString(), anyBoolean());
    }

    @Test
    void stopPausesAndResumePlays() throws Exception {
        service.processMidiMessage(showControl(DEVICE_ID, 0x02, ""));
        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).pause();

        service.processMidiMessage(showControl(DEVICE_ID, 0x03, ""));
        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).play();
    }

    @Test
    void readsOnlyTheCueNumberOutOfCueListAndPath() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0xF0);
        data.write(0x7F);
        data.write(DEVICE_ID);
        data.write(0x02);
        data.write(0x7F);
        data.write(0x01);
        data.writeBytes("7".getBytes(StandardCharsets.US_ASCII));
        data.write(0x00);
        data.writeBytes("2".getBytes(StandardCharsets.US_ASCII));
        data.write(0x00);
        data.writeBytes("1".getBytes(StandardCharsets.US_ASCII));
        data.write(0xF7);

        service.processMidiMessage(sysex(data));

        verify(midiCompositionSelectionService).selectByShowControlCue("7", true);
    }

    @Test
    void ignoresCommandsForAnotherDevice() throws Exception {
        service.processMidiMessage(showControl(DEVICE_ID + 1, 0x01, "12.5"));

        verify(midiCompositionSelectionService, never()).selectByShowControlCue(anyString(), anyBoolean());
    }

    @Test
    void acceptsTheAllCallDeviceId() throws Exception {
        service.processMidiMessage(showControl(0x7F, 0x01, "12.5"));

        verify(midiCompositionSelectionService).selectByShowControlCue("12.5", true);
    }

    @Test
    void ignoresShowControlWhileSwitchedOff() throws Exception {
        settings.setMidiShowControlEnabled(false);

        service.processMidiMessage(showControl(DEVICE_ID, 0x01, "12.5"));

        verify(midiCompositionSelectionService, never()).selectByShowControlCue(anyString(), anyBoolean());
    }

    @Test
    void machineControlPlayAndStopDriveTheTransport() throws Exception {
        service.processMidiMessage(machineControl(DEVICE_ID, 0x02));
        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).play();

        service.processMidiMessage(machineControl(DEVICE_ID, 0x01));
        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).stop();
    }

    @Test
    void machineControlLocatesToTheTargetPosition() throws Exception {
        // 01:02:03:04 at 25fps, sent as the locate target sub-command
        service.processMidiMessage(machineControl(DEVICE_ID, 0x44, 0x06, 0x01, (1 << 5) | 1, 2, 3, 4, 0));

        verify(playerService, timeout(VERIFY_TIMEOUT_MILLIS)).seek(3_723_160);
    }

    @Test
    void machineControlStandsBackWhileFollowingATimecode() throws Exception {
        when(midiTimecodeSlaveService.isLocked()).thenReturn(true);

        service.processMidiMessage(machineControl(DEVICE_ID, 0x02));

        Thread.sleep(100);
        verify(playerService, never()).play();
    }

    @Test
    void ignoresOtherUniversalRealTimeMessages() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0xF0);
        data.write(0x7F);
        data.write(DEVICE_ID);
        // Sub-ID 01 is MIDI timecode, not show or machine control
        data.write(0x01);
        data.write(0x01);
        data.write(0x00);
        data.write(0xF7);

        service.processMidiMessage(sysex(data));

        Thread.sleep(100);
        verify(playerService, never()).play();
        verify(midiCompositionSelectionService, never()).selectByShowControlCue(anyString(), anyBoolean());
    }

}
