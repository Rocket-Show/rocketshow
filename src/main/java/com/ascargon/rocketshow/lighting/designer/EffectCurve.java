package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A curve of the Rocket Show Designer: a value swinging over time on the channels of the fixtures of a preset, also
 * the timing the movements and the pixel effects run on.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectCurve extends Effect {

    // the tempo a curve synced to the beat falls back to: a preset previewed on its own is
    // not played inside a composition, so there is no tempo to take it from
    public final static double DEFAULT_BEATS_PER_MINUTE = 120;

    private String curveType = "sine";

    private List<FixtureCapability> capabilities = new ArrayList<>();
    private List<EffectCurveProfileChannels> channels = new ArrayList<>();

    // how the period is measured: "millis" holds it at a fixed time, "beats" locks it to
    // the tempo of the composition, so the curve keeps running with the music when the
    // tempo is changed or guessed again
    private String lengthMode = "millis";

    private double lengthMillis = 2500;

    // the period in beats, while it follows the tempo: 1 is a beat, 4 a bar in 4/4 time,
    // 0.5 an eighth
    private double lengthBeats = 4;

    private double phaseMillis = 0;

    // the phase in beats, while the period follows the tempo. keeping it musical as well
    // holds the curve at the same place inside its period when the tempo changes.
    private double phaseBeats = 0;

    private double amplitude = 1;
    private double position = 0.5;

    // the part of the period the curve is on (above its position), between 0 and 1. 0.5 is
    // the plain curve. every curve keeps its shape: a narrower width runs it faster and
    // rests it at its bottom for the rest of the period, a wider one rests it at its top. a
    // sine, a triangle and a square stay centered on their peak, a sawtooth keeps its jump.
    // a chase shows it as the part of the fixtures that are on at the same time: 0.1 lights
    // one fixture out of ten.
    private double dutyCycle = 0.5;

    // how long the curve keeps running:
    // "infinite" never stops, "cycles" runs runCycles full periods, "duration" runs
    // runDurationMillis. the chase delays the run of each fixture, so all of them run
    // their cycles completely.
    private String runMode = "infinite";
    private double runCycles = 1;
    private double runDurationMillis = 5000;

    // what a curve that has finished running puts on its channels: "hold" keeps the last
    // value, "base" stops applying the effect, which leaves the fixtures with whatever
    // the rest of the preset puts on them
    private String endMode = "hold";

    // how the curve is shifted from one fixture to the next (chasing):
    // "millis" shifts it by a fixed time, "beats" by a musical one that follows the tempo,
    // "spread" distributes phasingCycles full cycles over all fixtures of the preset, which
    // keeps the chase intact when the period or the number of fixtures changes.
    // all three values are signed: a negative one chases in the opposite direction.
    private String phasingMode = "millis";
    private double phasingMillis = 0;
    private double phasingBeats = 0;
    private double phasingCycles = 1;

    // how many fixtures share the same chase step (1 = each fixture on its own)
    private double phasingGroupSize = 1;

    // How far the curve swings on each fixture by its place in the fan of the preset: "none" as far on all of them,
    // "linear" the most on the first and the last one, the other way round on the one than on the other, "center" the
    // most on the first and the last one, the same way, "side" from not at all on the first one to the most on the last
    // one. The one in the middle stands still, except from the first one. A movement of the moving heads moves as far (a
    // line fanned "linear" opens and closes the fan of the heads).
    private String fanMode = "none";

    // A curve on the pan or the tilt turns every moving head by the same degrees: from the middle of what it reaches,
    // the whole curve from 0 to 1 spanning what a head reaches by default (540° of pan, 270° of tilt). A curve of a
    // project before it moves each head over a share of its own range instead.
    private boolean panTiltInDegrees = false;

    public boolean isPanTiltInDegrees() {
        return panTiltInDegrees;
    }

    // a value missing or null moves each head over a share of its range, like in the designer (and like curves did)
    public void setPanTiltInDegrees(Boolean panTiltInDegrees) {
        this.panTiltInDegrees = Boolean.TRUE.equals(panTiltInDegrees);
    }

    public String getFanMode() {
        return fanMode;
    }

    // a value missing or null swings as far on all the fixtures, like in the designer (and like curves always did)
    public void setFanMode(String fanMode) {
        this.fanMode = fanMode == null || fanMode.isEmpty() ? "none" : fanMode;
    }

    public String getCurveType() {
        return curveType;
    }

    public void setCurveType(String curveType) {
        this.curveType = curveType;
    }

    public List<FixtureCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<FixtureCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public List<EffectCurveProfileChannels> getChannels() {
        return channels;
    }

    public void setChannels(List<EffectCurveProfileChannels> channels) {
        this.channels = channels;
    }

    public String getLengthMode() {
        return lengthMode;
    }

    public void setLengthMode(String lengthMode) {
        this.lengthMode = lengthMode;
    }

    public double getLengthMillis() {
        return lengthMillis;
    }

    public void setLengthMillis(double lengthMillis) {
        this.lengthMillis = lengthMillis;
    }

    public double getLengthBeats() {
        return lengthBeats;
    }

    public void setLengthBeats(double lengthBeats) {
        this.lengthBeats = lengthBeats;
    }

    public double getPhaseMillis() {
        return phaseMillis;
    }

    public void setPhaseMillis(double phaseMillis) {
        this.phaseMillis = phaseMillis;
    }

    public double getPhaseBeats() {
        return phaseBeats;
    }

    public void setPhaseBeats(double phaseBeats) {
        this.phaseBeats = phaseBeats;
    }

    public double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(double amplitude) {
        this.amplitude = amplitude;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public double getPhasingMillis() {
        return phasingMillis;
    }

    public void setPhasingMillis(double phasingMillis) {
        this.phasingMillis = phasingMillis;
    }

    public double getPhasingBeats() {
        return phasingBeats;
    }

    public void setPhasingBeats(double phasingBeats) {
        this.phasingBeats = phasingBeats;
    }

    public String getPhasingMode() {
        return phasingMode;
    }

    public void setPhasingMode(String phasingMode) {
        this.phasingMode = phasingMode;
    }

    public double getPhasingCycles() {
        return phasingCycles;
    }

    public void setPhasingCycles(double phasingCycles) {
        this.phasingCycles = phasingCycles;
    }

    public double getPhasingGroupSize() {
        return phasingGroupSize;
    }

    public void setPhasingGroupSize(double phasingGroupSize) {
        this.phasingGroupSize = phasingGroupSize;
    }

    public double getDutyCycle() {
        return dutyCycle;
    }

    public void setDutyCycle(double dutyCycle) {
        this.dutyCycle = dutyCycle;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public double getRunCycles() {
        return runCycles;
    }

    public void setRunCycles(double runCycles) {
        this.runCycles = runCycles;
    }

    public double getRunDurationMillis() {
        return runDurationMillis;
    }

    public void setRunDurationMillis(double runDurationMillis) {
        this.runDurationMillis = runDurationMillis;
    }

    public String getEndMode() {
        return endMode;
    }

    public void setEndMode(String endMode) {
        this.endMode = endMode;
    }
}
