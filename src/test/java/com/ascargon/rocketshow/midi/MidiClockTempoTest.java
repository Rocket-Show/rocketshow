package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tempo of a MIDI clock from its ticks, 24 of them a beat.
 */
class MidiClockTempoTest {

    // the beats of the passed number of ticks at the passed tempo, from the passed time on (in nanoseconds)
    private static List<MidiClockTempo.Beat> ticks(MidiClockTempo tempo, int count, double beatsPerMinute, long fromNanos) {
        List<MidiClockTempo.Beat> beats = new ArrayList<>();
        double tickNanos = 60e9 / beatsPerMinute / 24;

        for (int i = 0; i < count; i++) {
            MidiClockTempo.Beat beat = tempo.tick(fromNanos + Math.round(i * tickNanos));

            if (beat != null) {
                beats.add(beat);
            }
        }

        return beats;
    }

    @Test
    void givesTheTempoOnceABeatFromTheSecondBeatOn() {
        MidiClockTempo tempo = new MidiClockTempo();

        List<MidiClockTempo.Beat> beats = ticks(tempo, 24 * 4 + 1, 128, 0);

        assertEquals(4, beats.size());
        beats.forEach(beat -> assertEquals(128, beat.beatsPerMinute(), 0.01));
        // joined while it runs: where its beats are isn't known
        beats.forEach(beat -> assertFalse(beat.onTheBeat()));
    }

    @Test
    void fallsOnTheBeatOnceStarted() {
        MidiClockTempo tempo = new MidiClockTempo();
        ticks(tempo, 30, 120, 0);

        tempo.start();
        List<MidiClockTempo.Beat> beats = ticks(tempo, 24 * 2 + 1, 90, 5_000_000_000L);

        assertEquals(2, beats.size());
        assertEquals(90, beats.getLast().beatsPerMinute(), 0.01);
        assertTrue(beats.getLast().onTheBeat());

        // stopped: the next start tells where the beats are again
        tempo.stop();
        assertFalse(ticks(tempo, 24 * 2 + 1, 90, 10_000_000_000L).getLast().onTheBeat());
    }

    @Test
    void forgetsTheBeatsOfAClockStoppedMeanwhile() {
        MidiClockTempo tempo = new MidiClockTempo();
        ticks(tempo, 24 * 2 + 1, 120, 0);

        // two seconds without a tick, then a slower clock
        List<MidiClockTempo.Beat> beats = ticks(tempo, 24 * 2 + 1, 60, 3_000_000_000L);

        assertEquals(60, beats.getFirst().beatsPerMinute(), 0.01);
    }
}
