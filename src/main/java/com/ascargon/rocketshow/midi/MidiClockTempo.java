package com.ascargon.rocketshow.midi;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Works out the tempo of a MIDI clock from its ticks: 24 of them make a beat. The tempo is the one of the last few
 * beats, given once a beat. A clock started (MIDI start) falls on the beat with its first tick, which tells where its
 * beats are: they are given as falling on the beat then. A clock joined while it runs only gives its tempo.
 *
 * @author Moritz A. Vieli
 */
public class MidiClockTempo {

    private static final int TICKS_PER_BEAT = 24;

    // the beats the tempo is taken from at the most
    private static final int BEATS = 4;

    // a tick this long after the one before (in nanoseconds) means the clock was stopped meanwhile
    private static final long GAP_NANOS = 1_000_000_000L;

    // a beat of the clock: its tempo and whether it falls on a beat of the clock started
    public record Beat(double beatsPerMinute, boolean onTheBeat) {
    }

    private final Deque<Long> beatNanos = new ArrayDeque<>();
    private long ticks = 0;
    private Long lastTickNanos;
    private boolean started = false;

    // A tick of the clock at the passed time (in nanoseconds). Gives a beat with the tempo on each 24th tick, once
    // the tempo is known, else nothing.
    public synchronized Beat tick(long nanos) {
        if (lastTickNanos != null && nanos - lastTickNanos > GAP_NANOS) {
            // the clock was stopped and runs again: where its beats are is not known anymore
            beatNanos.clear();
            ticks = 0;
            started = false;
        }

        lastTickNanos = nanos;
        boolean beat = ticks % TICKS_PER_BEAT == 0;
        ticks++;

        if (!beat) {
            return null;
        }

        beatNanos.addLast(nanos);

        while (beatNanos.size() > BEATS + 1) {
            beatNanos.removeFirst();
        }

        if (beatNanos.size() < 2) {
            return null;
        }

        double beatsPerMinute = 60e9 * (beatNanos.size() - 1) / (beatNanos.getLast() - beatNanos.getFirst());
        return new Beat(beatsPerMinute, started);
    }

    // the clock starts from the beginning: its next tick falls on the beat
    public synchronized void start() {
        beatNanos.clear();
        ticks = 0;
        lastTickNanos = null;
        started = true;
    }

    // the clock stops: where its beats are is not known anymore, once it runs again without a start
    public synchronized void stop() {
        started = false;
    }
}
