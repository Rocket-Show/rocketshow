package com.ascargon.rocketshow.lighting.designer;

/**
 * The tempo from outside: tapped, set, halved, doubled, nudged or given by a MIDI clock, and where its beats are. The
 * scenes picked by hand on the Play screen run on it, and so do the compositions without audio (and the default
 * composition): a composition with audio keeps its own tempo. Without a tempo from outside, the programmed one of the
 * composition or the project playing plays. A MIDI clock running wins over the taps.
 *
 * @author Moritz A. Vieli
 */
public interface TempoService {

    // A beat tapped. The first one after a pause is the first beat of a bar, at once. The ones after it give the tempo
    // and move the beats onto them smoothly.
    void tap();

    // Now is the first beat of a bar
    void beatOne();

    // The tempo from outside, set (null = back to the programmed one)
    void setBeatsPerMinute(Double beatsPerMinute);

    // Half or double the tempo playing
    void halve();

    void doubleTempo();

    // The tempo playing, a little faster (positive) or slower (negative), in beats per minute
    void nudge(double beatsPerMinute);

    // ---- a MIDI clock ----

    // A tick of a MIDI clock (24 a beat), at the passed time in nanoseconds
    void midiClockTick(long nanos);

    // The clock starts from the beginning: its next tick is the first beat of a bar
    void midiClockStart();

    // The clock continues from the song position passed last (see midiSongPosition)
    void midiClockContinue();

    void midiClockStop();

    // The song position of a MIDI clock, in sixteenth notes from the beginning: the clock goes on from there
    void midiSongPosition(int sixteenths);

    // ---- what plays ----

    // The tempo playing (null while nothing plays without a tempo from outside) and where it is from: "project" (the
    // programmed one), "tap", "set" or "clock"
    Double getBeatsPerMinute();

    String getSource();

    // Where the beats are at the moment, counted from when the clock started, and the beats of a bar
    double getBeats();

    int getBeatsPerBar();
}
