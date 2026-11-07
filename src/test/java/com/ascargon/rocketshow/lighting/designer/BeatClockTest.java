package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The beats of the tempo playing: run at the tempo, moved onto a beat smoothly, never backwards.
 */
class BeatClockTest {

    // the clock runs on at the passed real times (in steps of 10 ms) without ever going back
    private static void assertNeverBackwards(BeatClock clock, double fromMillis, double toMillis) {
        double before = clock.getBeats(fromMillis);

        for (double millis = fromMillis + 10; millis <= toMillis; millis += 10) {
            double beats = clock.getBeats(millis);
            assertTrue(beats >= before, "back from " + before + " to " + beats + " at " + millis);
            before = beats;
        }
    }

    @Test
    void runsAtItsTempoFromWhereItIsWhenTheTempoChanges() {
        BeatClock clock = new BeatClock(1000);

        // 120 BPM: two beats a second
        assertEquals(2, clock.getBeats(2000), 1e-9);

        clock.setBeatsPerMinute(60, 2000);
        assertEquals(2, clock.getBeats(2000), 1e-9);
        assertEquals(3, clock.getBeats(3000), 1e-9);
    }

    @Test
    void movesOntoTheClosestBeatOverTheNextBeatInsteadOfJumping() {
        BeatClock clock = new BeatClock(0);

        // tapped at 2.3 beats: 0.3 beats to fall back over the next 500 ms
        clock.alignBeat(1150);
        assertEquals(2.3, clock.getBeats(1150), 1e-9);
        assertNeverBackwards(clock, 1150, 2000);

        // a beat later it is on the beat
        assertEquals(3, clock.getBeats(1650), 1e-9);
        assertEquals(4, clock.getBeats(2150), 1e-9);

        // tapped at 4.5 beats: half a beat to catch up
        clock.alignBeat(2400);
        assertEquals(6, clock.getBeats(2900), 1e-9);
    }

    @Test
    void keepsMovingOntoTheBeatWhileTheTempoChanges() {
        BeatClock clock = new BeatClock(0);
        clock.alignBeat(1150);

        // half way through, the tempo changes: the rest of the shift still comes
        clock.setBeatsPerMinute(60, 1400);
        double atChange = clock.getBeats(1400);
        assertEquals(2.3 + 0.5 - 0.15, atChange, 1e-9);

        // by the end of the shift: the beats at 60 BPM plus what was left to fall back
        assertEquals(atChange + 0.25 - 0.15, clock.getBeats(1650), 1e-9);
        assertEquals(clock.getBeats(1650) + 1, clock.getBeats(2650), 1e-9);
    }

    @Test
    void makesNowTheFirstBeatOfABar() {
        BeatClock clock = new BeatClock(0);

        // at 5.2 beats: the next bar of 4 is at 8, which the closest beat (5) jumps to; the rest falls back
        clock.beatOne(4, false, 2600);
        assertEquals(8.2, clock.getBeats(2600), 1e-9);
        assertEquals(9, clock.getBeats(3100), 1e-9);
        assertNeverBackwards(clock, 2600, 4000);

        // jumping there at once
        clock.beatOne(3, true, 4000);
        assertEquals(0, clock.getBeats(4000) % 3, 1e-9);
        assertEquals(0, clock.getBeatOfBar(3, 4000));
        assertEquals(1, clock.getBeatOfBar(3, 4500));
    }

    @Test
    void jumpsToAPositionSet() {
        BeatClock clock = new BeatClock(0);
        clock.alignBeat(1150);

        clock.setBeats(16, 1200);
        assertEquals(16, clock.getBeats(1200), 1e-9);
        assertEquals(17, clock.getBeats(1700), 1e-9);
    }
}
