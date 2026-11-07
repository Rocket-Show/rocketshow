package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.DefaultSetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.session.DefaultSessionService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tempo from outside: tapped, set, halved, doubled, nudged or of a MIDI clock, and where its beats are.
 */
class DefaultTempoServiceTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private DesignerService designerService;
    private BeatClock beatClock;
    private Double outside;
    private final long[] now = {100000};

    @BeforeEach
    void setUp() {
        settingsService = TestSettings.inDirectory(directory);
        beatClock = new BeatClock(now[0]);
        outside = null;

        // the engine: the tempo from outside, else the programmed one of 128 BPM, in bars of 4
        designerService = mock(DesignerService.class);
        when(designerService.getBeatClock()).thenReturn(beatClock);
        when(designerService.getBeatsPerBar()).thenReturn(4);
        org.mockito.Mockito.doAnswer(invocation -> {
            outside = invocation.getArgument(0);

            if (outside != null) {
                beatClock.setBeatsPerMinute(outside, now[0]);
            }

            return null;
        }).when(designerService).setOutsideBeatsPerMinute(org.mockito.ArgumentMatchers.any());
        when(designerService.getOutsideBeatsPerMinute()).thenAnswer(invocation -> outside);
        when(designerService.getTempoBeatsPerMinute()).thenAnswer(invocation -> outside != null ? outside : 128.0);
    }

    private DefaultTempoService tempoService() {
        DefaultTempoService tempoService = new DefaultTempoService(designerService, new DefaultSessionService(settingsService, new DefaultSetService()), mock(NotificationService.class), mock(PlayerService.class), new DefaultSetService());
        tempoService.setClock(() -> now[0]);
        return tempoService;
    }

    @Test
    void takesTheTempoFromTheBeatsTappedTheFirstOneStartingABar() {
        DefaultTempoService tempoService = tempoService();
        assertEquals("project", tempoService.getSource());
        assertEquals(128.0, tempoService.getBeatsPerMinute());

        // the first beat tapped is the first one of a bar, at once
        now[0] += 1300;
        tempoService.tap();
        assertEquals(0, beatClock.getBeatOfBar(4, now[0]));
        assertEquals(0, beatClock.getBeats(now[0]) % 4, 1e-9);
        assertEquals("project", tempoService.getSource());

        now[0] += 500;
        tempoService.tap();
        now[0] += 500;
        tempoService.tap();
        assertEquals(120.0, tempoService.getBeatsPerMinute());
        assertEquals("tap", tempoService.getSource());

        // a pause starts tapping anew
        now[0] += 5000;
        tempoService.tap();
        now[0] += 400;
        tempoService.tap();
        assertEquals(150.0, tempoService.getBeatsPerMinute());

        // remembered over a restart
        outside = null;
        tempoService();
        assertEquals(150.0, outside);
    }

    @Test
    void setsHalvesDoublesAndNudgesTheTempo() {
        DefaultTempoService tempoService = tempoService();

        // from the programmed tempo
        tempoService.doubleTempo();
        assertEquals(256.0, tempoService.getBeatsPerMinute());
        assertEquals("set", tempoService.getSource());

        tempoService.halve();
        tempoService.halve();
        assertEquals(64.0, tempoService.getBeatsPerMinute());

        tempoService.nudge(1);
        assertEquals(65.0, tempoService.getBeatsPerMinute());

        // as far as it goes
        tempoService.setBeatsPerMinute(1000.0);
        assertEquals(300.0, tempoService.getBeatsPerMinute());

        // back to the programmed one
        tempoService.setBeatsPerMinute(null);
        assertNull(outside);
        assertEquals("project", tempoService.getSource());
        assertEquals(128.0, tempoService.getBeatsPerMinute());
    }

    @Test
    void keepsATempoTappedTappedWhenItIsHalved() {
        DefaultTempoService tempoService = tempoService();
        tempoService.tap();
        now[0] += 500;
        tempoService.tap();

        tempoService.halve();
        assertEquals(60.0, tempoService.getBeatsPerMinute());
        assertEquals("tap", tempoService.getSource());
    }

    // the ticks of a MIDI clock at the passed tempo, for the passed beats, from now on
    private void tick(DefaultTempoService tempoService, double beatsPerMinute, int beats) {
        double tickMillis = 60000 / beatsPerMinute / 24;

        for (int i = 0; i < beats * 24; i++) {
            tempoService.midiClockTick(Math.round(now[0] * 1e6));
            now[0] += Math.round(tickMillis);
        }
    }

    @Test
    void followsAMidiClockStartedOnTheFirstBeatOfABarWithoutRememberingIt() {
        DefaultTempoService tempoService = tempoService();
        now[0] += 700;

        tempoService.midiClockStart();
        long start = now[0];
        tick(tempoService, 125, 4);

        assertEquals(125.0, tempoService.getBeatsPerMinute(), 0.5);
        assertEquals("clock", tempoService.getSource());

        // started on the first beat of a bar
        assertEquals(0, beatClock.getBeats(start) % 4, 1e-6);

        // the clock wins over the taps
        tempoService.tap();
        assertEquals("clock", tempoService.getSource());

        // not remembered over a restart
        outside = null;
        tempoService();
        assertNull(outside);
    }

    @Test
    void continuesAMidiClockFromItsSongPosition() {
        DefaultTempoService tempoService = tempoService();

        // the song position of 2 bars and a beat (36 sixteenths)
        tempoService.midiSongPosition(36);
        tempoService.midiClockContinue();
        long start = now[0];
        tick(tempoService, 120, 1);

        assertEquals(1, beatClock.getBeatOfBar(4, start));
    }

    @Test
    void letsTheTapsGiveTheTempoAgainOnceTheMidiClockStopped() {
        DefaultTempoService tempoService = tempoService();
        tempoService.midiClockStart();
        tick(tempoService, 125, 3);
        tempoService.midiClockStop();

        tempoService.tap();
        now[0] += 400;
        tempoService.tap();
        assertEquals(150.0, tempoService.getBeatsPerMinute());
        assertEquals("tap", tempoService.getSource());
    }
}
