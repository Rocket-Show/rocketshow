package com.ascargon.rocketshow.lighting.designer;

/**
 * The beats of the tempo playing, counted from when the clock was started: where they are at a moment of real time.
 * <p>
 * The clock runs at its tempo. A beat tapped or a tempo changed moves it onto the beat smoothly instead of jumping: it
 * runs a little faster or slower for one beat, until it is there. So it never runs backwards and never skips, and the
 * effects following it don't stutter. Only a clock started anew or set to a position jumps.
 *
 * @author Moritz A. Vieli
 */
public class BeatClock {

    // the beats at the anchor and the real time of it, the tempo since then
    private double anchorBeats = 0;
    private double anchorRealMillis = 0;
    private double beatsPerMinute = EffectCurve.DEFAULT_BEATS_PER_MINUTE;

    // the beats still to catch up (or to fall back, if negative) by the passed real time, from the anchor on
    private double shiftBeats = 0;
    private double shiftEndRealMillis = 0;

    public BeatClock(double realMillis) {
        anchorRealMillis = realMillis;
    }

    // where the clock is at the passed real time, in beats
    public synchronized double getBeats(double realMillis) {
        double elapsedMillis = realMillis - anchorRealMillis;
        double beats = anchorBeats + elapsedMillis * beatsPerMinute / 60000;

        if (shiftBeats != 0) {
            double shiftMillis = shiftEndRealMillis - anchorRealMillis;
            double progress = shiftMillis <= 0 ? 1 : Math.max(0, Math.min(1, elapsedMillis / shiftMillis));
            beats += shiftBeats * progress;
        }

        return beats;
    }

    public synchronized double getBeatsPerMinute() {
        return beatsPerMinute;
    }

    // the anchor moved to the passed real time: the part of the shift still to come stays to come
    private void rebase(double realMillis) {
        double beats = getBeats(realMillis);

        if (shiftBeats != 0) {
            double shiftMillis = shiftEndRealMillis - anchorRealMillis;
            double progress = shiftMillis <= 0 ? 1 : Math.max(0, Math.min(1, (realMillis - anchorRealMillis) / shiftMillis));
            shiftBeats = progress >= 1 ? 0 : shiftBeats * (1 - progress);
        }

        anchorBeats = beats;
        anchorRealMillis = realMillis;
    }

    // run at another tempo from the passed moment on, from where the clock is
    public synchronized void setBeatsPerMinute(double beatsPerMinute, double realMillis) {
        if (!(beatsPerMinute > 0) || beatsPerMinute == this.beatsPerMinute) {
            return;
        }

        rebase(realMillis);
        this.beatsPerMinute = beatsPerMinute;
    }

    // catch the passed beats up (or fall them back) smoothly, over the passed number of beats. At most half of them,
    // so the clock runs at half its tempo at the slowest, never backwards.
    private void shift(double beats, double overBeats, double realMillis) {
        anchorBeats = getBeats(realMillis);
        anchorRealMillis = realMillis;
        shiftBeats = beats;
        shiftEndRealMillis = realMillis + Math.max(overBeats, 2 * Math.abs(beats)) * 60000 / beatsPerMinute;
    }

    // A beat falls on the passed moment (e.g. a beat tapped): the clock moves onto the closest whole beat over the next
    // beat. The shift still to come of an earlier beat is dropped for it.
    public synchronized void alignBeat(double realMillis) {
        double beats = getBeats(realMillis);
        shift(Math.round(beats) - beats, 1, realMillis);
    }

    // The first beat of a bar falls on the passed moment. Jumping ahead to the next whole bar, the beats repeat the
    // same: so does an effect running for a beat. What is off by less than a beat is caught up over the next beat, unless
    // it jumps there at once (e.g. the first beat tapped after a pause, when nothing runs in time yet).
    public synchronized void beatOne(int beatsPerBar, boolean jump, double realMillis) {
        int bar = Math.max(1, beatsPerBar);
        double beats = getBeats(realMillis);

        if (jump) {
            setBeats(Math.ceil(beats / bar - 1e-9) * bar, realMillis);
            return;
        }

        double closest = Math.round(beats);
        double barStart = Math.ceil(closest / bar - 1e-9) * bar;

        anchorBeats = beats + (barStart - closest);
        anchorRealMillis = realMillis;
        shiftBeats = 0;
        shift(closest - beats, 1, realMillis);
    }

    // where the clock is at the passed moment, at once (e.g. a song position of a MIDI clock)
    public synchronized void setBeats(double beats, double realMillis) {
        anchorBeats = beats;
        anchorRealMillis = realMillis;
        shiftBeats = 0;
    }

    // the beat of the bar the clock is in at the passed moment, from 0
    public synchronized int getBeatOfBar(int beatsPerBar, double realMillis) {
        int bar = Math.max(1, beatsPerBar);
        double beats = getBeats(realMillis);
        return (int) (((Math.floor(beats + 1e-9) % bar) + bar) % bar);
    }
}
