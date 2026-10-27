package com.ascargon.rocketshow.midi;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sending MIDI timecode while a composition plays, like a receiver (e.g. a lighting desk or a video server) gets it.
 */
class DefaultMidiTimecodeServiceTest {

    private record Sent(MidiMessage message, long nanos) {
    }

    private final Settings settings = new Settings();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private DefaultMidiTimecodeService midiTimecodeService;

    @BeforeEach
    void setUp() {
        settings.setMidiTimecodeEnabled(true);
        settings.setMidiTimecodeFrameRate(MidiTimecodeFrameRate.FPS_25);
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        MidiDeviceOutService midiDeviceOutService = mock(MidiDeviceOutService.class);
        when(midiDeviceOutService.isConnected()).thenReturn(true);
        doAnswer(invocation -> {
            sent.add(new Sent(invocation.getArgument(0), System.nanoTime()));
            return null;
        }).when(midiDeviceOutService).sendMessage(any());

        midiTimecodeService = new DefaultMidiTimecodeService(settingsService, midiDeviceOutService);
    }

    @AfterEach
    void tearDown() throws Exception {
        Method close = DefaultMidiTimecodeService.class.getDeclaredMethod("close");
        close.setAccessible(true);
        close.invoke(midiTimecodeService);
    }

    private static void waitMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    // A composition playing from the passed position on
    private static LongSupplier playingFrom(long positionMillis) {
        long startNanos = System.nanoTime();
        return () -> positionMillis + (System.nanoTime() - startNanos) / 1_000_000;
    }

    private List<ShortMessage> quarterFrames() {
        return sent.stream().map(Sent::message).filter(message -> message instanceof ShortMessage).map(message -> (ShortMessage) message).toList();
    }

    @Test
    void sendsTheFullTimecodeAtTheStart() throws Exception {
        // 00:01:02:12 at 25 frames per second
        midiTimecodeService.start(this, () -> 62_480);
        midiTimecodeService.stop(this);

        SysexMessage fullFrame = assertInstanceOf(SysexMessage.class, sent.getFirst().message());
        assertArrayEquals(new byte[]{(byte) 0xF0, 0x7F, 0x7F, 0x01, 0x01, 0x20, 1, 2, 12, (byte) 0xF7}, fullFrame.getMessage());
    }

    @Test
    void sendsFourQuarterFramesPerFrame() throws Exception {
        midiTimecodeService.start(this, playingFrom(0));
        waitMillis(1000);
        midiTimecodeService.stop(this);

        // 100 per second at 25 frames per second, the scheduler of a test machine is not exact
        int count = quarterFrames().size();
        assertTrue(count > 70 && count < 110, count + " quarter frames");

        // In order, from the frame to the hours and the rate
        for (int i = 0; i < quarterFrames().size(); i++) {
            assertEquals(0xF1, quarterFrames().get(i).getStatus());
            assertEquals(i % 8, quarterFrames().get(i).getData1() >> 4);
        }
    }

    @Test
    void sendsNothingWhenStoppedOrDisabled() throws Exception {
        midiTimecodeService.start(this, playingFrom(0));
        waitMillis(100);
        midiTimecodeService.stop(this);
        waitMillis(50);
        int count = sent.size();
        waitMillis(200);
        assertEquals(count, sent.size());

        settings.setMidiTimecodeEnabled(false);
        midiTimecodeService.start(this, playingFrom(0));
        waitMillis(200);
        assertEquals(count, sent.size());
    }

    @Test
    void keepsTheTimecodeOfAnotherComposition() throws Exception {
        Object sample = new Object();
        midiTimecodeService.start(this, playingFrom(0));

        // E.g. a sample stopping, while the composition plays
        midiTimecodeService.stop(sample);
        int count = sent.size();
        waitMillis(200);

        assertTrue(sent.size() > count + 10);
    }

    // The timecodes a receiver puts together from the quarter frames: 8 of them make one, the time when the first one
    // was sent (the receiver adds the 2 frames they take)
    @Test
    void sendsNoJumpsOnANewSecond() throws Exception {
        List<String> jumps = new ArrayList<>();

        // Start at different moments in front of a new second, to hit it with every quarter frame of 8
        for (int run = 0; run < 8; run++) {
            sent.clear();
            long startMillis = 9_700 + run * 10;
            long startNanos = System.nanoTime();
            midiTimecodeService.start(this, () -> startMillis + (System.nanoTime() - startNanos) / 1_000_000);
            waitMillis(400);
            midiTimecodeService.stop(this);

            List<Sent> quarterFrames = sent.stream().filter(sent -> sent.message() instanceof ShortMessage).toList();
            for (int i = 0; i + 8 <= quarterFrames.size(); i += 8) {
                int[] pieces = new int[8];
                for (int piece = 0; piece < 8; piece++) {
                    ShortMessage message = (ShortMessage) quarterFrames.get(i + piece).message();
                    assertEquals(piece, message.getData1() >> 4);
                    pieces[piece] = message.getData1() & 0x0F;
                }
                int frame = pieces[0] | (pieces[1] << 4);
                int second = pieces[2] | (pieces[3] << 4);
                long frames = second * 25L + frame;

                // The position when the first of the 8 quarter frames was sent, at most a frame earlier or later
                long firstMillis = startMillis + (quarterFrames.get(i).nanos() - startNanos) / 1_000_000;
                long lastMillis = startMillis + (quarterFrames.get(i + 7).nanos() - startNanos) / 1_000_000;
                if (frames < firstMillis / 40 - 1 || frames > firstMillis / 40 + 1) {
                    jumps.add(String.format("%02d:%02d at %d to %d ms", second, frame, firstMillis, lastMillis));
                }
            }
        }

        assertEquals(List.of(), jumps);
    }

}
