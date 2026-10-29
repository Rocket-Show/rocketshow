package com.ascargon.rocketshow.midi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiTimecodeClockTest {

    private static long nanos(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Test
    void interpolatesBetweenReceivedPositions() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(10_000, nanos(1_000), true);

        assertTrue(clock.isRunning(nanos(1_000)));
        // No new position received, but the clock keeps running locally
        assertEquals(10_033, clock.getPositionMillis(nanos(1_033)));
    }

    @Test
    void followsAMasterRunningAtTheSameRate() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(0, nanos(0), true);

        // A position every two frames at 30fps, exactly on time
        for (long millis = 67; millis <= 5_000; millis += 67) {
            clock.observe(millis, nanos(millis), false);
        }

        assertEquals(5_000, clock.getPositionMillis(nanos(5_000)), 2);
    }

    @Test
    void easesOutASmallOffsetInsteadOfJumping() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(0, nanos(0), true);

        // The master is 40ms ahead of where the clock thinks it is
        clock.observe(107, nanos(67), false);

        // A single observation only corrects a fraction of it, so the position stays smooth
        long positionMillis = clock.getPositionMillis(nanos(67));
        assertTrue(positionMillis > 67 && positionMillis < 107,
                "Expected a partial correction, but got " + positionMillis);

        assertFalse(clock.consumeJump());
    }

    @Test
    void reAnchorsAndReportsAJumpWhenTheMasterRelocates() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(0, nanos(0), true);
        clock.consumeJump();

        clock.observe(60_000, nanos(67), false);

        assertEquals(60_000, clock.getPositionMillis(nanos(67)));
        assertTrue(clock.consumeJump());
        assertFalse(clock.consumeJump());
    }

    @Test
    void alwaysReportsAJumpForALocate() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(1_000, nanos(0), true);
        clock.consumeJump();

        // Parked masters re-send their position; a locate re-anchors even without an error
        clock.observe(1_000, nanos(67), true);

        assertTrue(clock.consumeJump());
    }

    @Test
    void stopsRunningWhenNothingIsReceivedAnymore() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(0, nanos(0), true);

        assertTrue(clock.isRunning(nanos(200)));
        assertFalse(clock.isRunning(nanos(1_000)));
    }

    @Test
    void startsOverAfterTheTimecodeStoppedAndCameBack() {
        MidiTimecodeClock clock = new MidiTimecodeClock();

        clock.observe(0, nanos(0), true);
        assertFalse(clock.isRunning(nanos(1_000)));

        clock.observe(120_000, nanos(2_000), false);

        assertTrue(clock.isRunning(nanos(2_000)));
        assertEquals(120_000, clock.getPositionMillis(nanos(2_000)));
    }

}
