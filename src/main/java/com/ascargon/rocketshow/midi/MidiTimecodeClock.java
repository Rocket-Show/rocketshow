package com.ascargon.rocketshow.midi;

import java.util.concurrent.TimeUnit;

/**
 * A continuously running position clock that is disciplined by incoming MIDI timecode.
 * <p>
 * MIDI timecode only carries a position every two frames (67ms at 30fps) and arrives over a MIDI
 * transport and a Java thread, so it is both coarse and jittery. Using it directly as a playback
 * position would make the lighting output and the action triggers stutter. Instead this clock runs
 * freely on the local monotonic clock and each received position is used to correct its phase and
 * its rate — the same idea as a phase-locked loop — which yields a smooth position at any resolution
 * that still follows the master exactly.
 * <p>
 * Corrections are gentle: a large error is treated as a jump (the master re-located or started
 * somewhere else) and re-anchors the clock hard, while a small error is eased out over roughly a
 * second. All methods are safe to call from the MIDI input thread and the control loop.
 */
class MidiTimecodeClock {

    // The timecode is considered stopped if nothing was received for this long. MTC sends four
    // messages per frame, so this tolerates a fair number of lost messages before giving up.
    private static final long FREEWHEEL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    // An error above this is not drift but a jump (a locate, a loop in the master, a start from a
    // different position) and re-anchors the clock instead of being eased out.
    private static final long JUMP_THRESHOLD_MILLIS = 250;

    // Fraction of the remaining phase error corrected per received position
    private static final double PHASE_GAIN = 0.1;

    // Smoothing of the measured master rate (per received position). Deliberately small: a single
    // observation interval is short enough that thread jitter dominates a single measurement.
    private static final double RATE_GAIN = 0.02;

    // The master's rate is never assumed to be further off than this (it also keeps a garbled
    // observation from running away with the clock)
    private static final double MAX_RATE_DEVIATION = 0.05;

    private boolean running = false;
    private boolean jumped = false;

    private long anchorNanos;
    private double anchorPositionMillis;
    private double rate = 1;

    private long lastObservationNanos;
    private long lastObservationPositionMillis;

    /**
     * Feed a position decoded from the MIDI input.
     *
     * @param positionMillis the absolute position reported by the timecode master
     * @param nowNanos       the local monotonic time the message was received at
     * @param locate         true if this was a full-frame locate rather than a rolling position
     */
    synchronized void observe(long positionMillis, long nowNanos, boolean locate) {
        if (!running || locate || nowNanos - lastObservationNanos > FREEWHEEL_NANOS) {
            anchor(positionMillis, nowNanos, running);
            return;
        }

        double predictedMillis = positionAt(nowNanos);
        double errorMillis = positionMillis - predictedMillis;

        if (Math.abs(errorMillis) > JUMP_THRESHOLD_MILLIS) {
            anchor(positionMillis, nowNanos, true);
            return;
        }

        // Estimate how fast the master runs compared to the local clock, so the clock keeps
        // following it between observations instead of drifting away again after every correction.
        double elapsedMillis = (nowNanos - lastObservationNanos) / 1_000_000.0;
        if (elapsedMillis > 0) {
            double observedRate = (positionMillis - lastObservationPositionMillis) / elapsedMillis;
            if (observedRate > 0) {
                rate = clampRate(rate * (1 - RATE_GAIN) + observedRate * RATE_GAIN);
            }
        }

        // Ease out the remaining phase error
        anchorPositionMillis = predictedMillis + errorMillis * PHASE_GAIN;
        anchorNanos = nowNanos;

        lastObservationNanos = nowNanos;
        lastObservationPositionMillis = positionMillis;
    }

    private void anchor(long positionMillis, long nowNanos, boolean isJump) {
        anchorPositionMillis = positionMillis;
        anchorNanos = nowNanos;
        rate = 1;
        lastObservationNanos = nowNanos;
        lastObservationPositionMillis = positionMillis;
        running = true;
        jumped |= isJump;
    }

    /**
     * The interpolated position of the timecode master, in milliseconds.
     */
    synchronized long getPositionMillis(long nowNanos) {
        return Math.max(0, Math.round(positionAt(nowNanos)));
    }

    private double positionAt(long nowNanos) {
        return anchorPositionMillis + (nowNanos - anchorNanos) / 1_000_000.0 * rate;
    }

    /**
     * True while the master keeps sending timecode. Turns false once nothing was received for
     * {@link #FREEWHEEL_NANOS}, which is how a stopped master is detected.
     */
    synchronized boolean isRunning(long nowNanos) {
        if (running && nowNanos - lastObservationNanos > FREEWHEEL_NANOS) {
            running = false;
        }

        return running;
    }

    /**
     * True (once) if the master jumped rather than rolled since this was last called, so the caller
     * can re-locate instead of trying to catch up.
     */
    synchronized boolean consumeJump() {
        boolean result = jumped;
        jumped = false;
        return result;
    }

    synchronized void reset() {
        running = false;
        jumped = false;
        rate = 1;
    }

    private static double clampRate(double rate) {
        return Math.max(1 - MAX_RATE_DEVIATION, Math.min(1 + MAX_RATE_DEVIATION, rate));
    }

}
