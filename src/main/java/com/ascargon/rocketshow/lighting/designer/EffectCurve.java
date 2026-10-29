package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectCurve extends Effect {

    private final static Logger logger = LoggerFactory.getLogger(EffectCurve.class);

    // a cycle border is never hit exactly when the curve is sampled, so the value held
    // after a run is taken just before it
    private final static double CYCLE_EPSILON = 1e-9;

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

    // how long a beat lasts at the passed tempo
    public static double getBeatMillis(Double beatsPerMinute) {
        double bpm = beatsPerMinute != null && beatsPerMinute > 0 ? beatsPerMinute : DEFAULT_BEATS_PER_MINUTE;

        return 60000 / bpm;
    }

    // whether the curve follows the tempo instead of a fixed time
    @JsonIgnore
    public boolean isBeatSynced() {
        return "beats".equals(this.lengthMode);
    }

    // the period of the curve at the tempo it is played at
    public double getLengthMillis(Double beatsPerMinute) {
        if (!this.isBeatSynced()) {
            return this.lengthMillis;
        }

        // a period of zero would divide by zero when the cycle is sampled
        return Math.max(this.lengthBeats * getBeatMillis(beatsPerMinute), 1);
    }

    // the time the curve is shifted by inside its period
    public double getPhaseMillis(Double beatsPerMinute) {
        if (!this.isBeatSynced()) {
            return this.phaseMillis;
        }

        return this.phaseBeats * getBeatMillis(beatsPerMinute);
    }

    // the time the curve is shifted by for the passed fixture of the preset
    public double getPhasingMillis(Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        // a number of fixtures: the designer rounds it
        long groupSize = Math.max(Math.round(this.phasingGroupSize), 1);
        double step = Math.floor((double) (fixtureIndex == null ? 0 : fixtureIndex) / groupSize);

        if ("spread".equals(this.phasingMode)) {
            // distribute the cycles over all chase steps of the preset
            int count = fixtureCount == null || fixtureCount == 0 ? 1 : fixtureCount;
            double steps = Math.max(Math.ceil((double) count / groupSize), 1);
            return step / steps * this.phasingCycles * this.getLengthMillis(beatsPerMinute);
        }

        if ("beats".equals(this.phasingMode)) {
            return step * this.phasingBeats * getBeatMillis(beatsPerMinute);
        }

        return step * this.phasingMillis;
    }

    // how long the curve runs, measured from the moment its fixture starts, or null if it
    // never stops
    @JsonIgnore
    public Double getRunEndMillis(Double beatsPerMinute) {
        if ("cycles".equals(this.runMode)) {
            // a number of cycles: the designer rounds it
            return Math.max(Math.round(this.runCycles), 1) * this.getLengthMillis(beatsPerMinute);
        }

        if ("duration".equals(this.runMode)) {
            return Math.max(this.runDurationMillis, 0);
        }

        return null;
    }

    // how long one pass of a curve that stops takes, from the moment the first fixture
    // starts until the last one has finished, plus a rest that shows what it leaves behind.
    // a preset that is not placed in a composition has no moment it starts at, so it is
    // previewed by repeating this pass. null for a curve that never stops.
    @JsonIgnore
    public Double getRunLoopMillis(Integer fixtureCount, Double beatsPerMinute) {
        Double runEndMillis = this.getRunEndMillis(beatsPerMinute);

        if (runEndMillis == null) {
            return null;
        }

        // the chase delays the fixtures against each other, so the pass is only over once
        // the last of them has run
        int count = fixtureCount == null || fixtureCount == 0 ? 1 : fixtureCount;
        double chaseMillis = Math.abs(this.getPhasingMillis(Math.max(count - 1, 0), fixtureCount, beatsPerMinute));

        return Math.max((runEndMillis + chaseMillis) * 1.25, 1);
    }

    // the value the curve puts on its channels, or null if it does not apply at this
    // moment - the fixtures keep whatever the rest of the preset puts on them then
    @Override
    public Double getValueAtMillis(double timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        double lengthMillis = this.getLengthMillis(beatsPerMinute);
        double phasingMillis = this.getPhasingMillis(fixtureIndex, fixtureCount, beatsPerMinute);
        double phaseMillis = this.getPhaseMillis(beatsPerMinute);
        double phase = phaseMillis + phasingMillis;

        Double runEndMillis = this.getRunEndMillis(beatsPerMinute);

        if (runEndMillis != null) {
            // the chase delays the whole run of a fixture, so each of them runs its cycles
            // completely. the phase only shifts the curve inside the run.
            double runMillis = timeMillis - phasingMillis;

            if (runMillis < 0) {
                // this fixture has not started yet
                return null;
            }

            if (runMillis >= runEndMillis) {
                if ("base".equals(this.endMode)) {
                    return null;
                }

                return this.getCurveValue(this.getCyclePosition(runEndMillis - phaseMillis, true, lengthMillis));
            }
        }

        return this.getCurveValue(this.getCyclePosition(timeMillis - phase, false, lengthMillis));
    }

    // the position inside the current cycle, between 0 and 1
    private double getCyclePosition(double shiftedMillis, boolean atRunEnd, double lengthMillis) {
        double cyclePosition = ((shiftedMillis / lengthMillis) % 1 + 1) % 1;

        if (atRunEnd && cyclePosition < CYCLE_EPSILON) {
            // a run of full cycles ends exactly on a cycle border, where the curve has already
            // jumped back to its beginning -> hold what it showed just before it
            return 1 - CYCLE_EPSILON;
        }

        return cyclePosition;
    }

    // the duty cycle, kept inside the range the curves are defined for
    private double getClampedDutyCycle() {
        return Math.max(Math.min(this.dutyCycle, 1), 0);
    }

    // remaps the position inside the cycle of a sine or a triangle, so the curve is on over
    // the duty cycle. it stays symmetric around its peak: below 50 % it runs faster and
    // rests at its trough for the rest of the cycle, above it rests at its peak and dips
    // faster. the plain 50 % leaves it untouched.
    private double warpCyclePosition(double cyclePosition) {
        double duty = this.getClampedDutyCycle();

        if (duty == 0.5) {
            return cyclePosition;
        }

        // never on or never off: the curve rests at its trough or its peak
        if (duty <= 0) {
            return 0.75;
        }

        if (duty >= 1) {
            return 0.25;
        }

        // both curves are symmetric around their peak at a quarter of the cycle, so only the
        // distance from it counts: 0 on the peak, 0.5 on the trough
        double fromPeak = Math.abs((cyclePosition + 0.25) % 1 - 0.5);

        // the plain curve falls from its peak to its trough over half the cycle and crosses
        // its position halfway. warped, it crosses it at half the duty cycle and falls over
        // the shorter of the on and off parts, so it neither overshoots its peak nor its
        // trough and rests at them over the rest.
        double fall = Math.min(duty, 1 - duty);
        double warpedFromPeak = Math.max(Math.min(0.25 + (fromPeak - duty / 2) / fall * 0.5, 0.5), 0);

        return 0.25 + warpedFromPeak;
    }

    // how far a sawtooth has ramped up at a position inside its cycle, between 0 (its
    // bottom) and 1 (its top), before it jumps back at the end of the cycle. it keeps the
    // jump where it is and is on over the duty cycle before it: below 50 % it ramps faster
    // and rests at its bottom before, above it ramps right after the jump and rests at its
    // top. the plain 50 % ramps over the whole cycle.
    private double getRamp(double cyclePosition) {
        double duty = this.getClampedDutyCycle();

        if (duty == 0.5) {
            return cyclePosition;
        }

        // never on or never off: the curve rests at its bottom or its top
        if (duty <= 0) {
            return 0;
        }

        if (duty >= 1) {
            return 1;
        }

        // the ramp crosses its middle where the duty cycle begins and takes twice the shorter
        // of the on and off parts, so it fits into the cycle on both sides of its middle
        double rampLength = 2 * Math.min(duty, 1 - duty);

        return Math.max(Math.min(0.5 + (cyclePosition - (1 - duty)) / rampLength, 1), 0);
    }

    // the value of the curve at a position inside its cycle, between 0 and 1. every curve swings over its whole
    // amplitude around its position (before project version 8, the curves except the square swung over half of it).
    private double getCurveValue(double cyclePosition) {
        // Calculate the value between 0 and 1 according to the curve
        double value = 0d;
        double warped;

        switch (this.curveType) {
            case "sine":
                warped = this.warpCyclePosition(cyclePosition);
                value = position + amplitude / 2 * CycleSine.sin(warped);
                break;
            case "square":
                // on while the sine is above its position: over the duty cycle, centered on
                // the peak of the sine at a quarter of the cycle
                double duty = this.getClampedDutyCycle();
                double fromOn = ((cyclePosition - 0.25 + duty / 2) % 1 + 1) % 1;

                if (fromOn < duty) {
                    value = position + amplitude / 2;
                } else {
                    value = position - amplitude / 2;
                }
                break;
            case "triangle":
                // rises and falls linearly, in phase with the sine
                warped = this.warpCyclePosition(cyclePosition);
                value = position + amplitude / 2 * (1 - 4 * Math.abs((warped + 0.25) % 1 - 0.5));
                break;
            case "sawtooth":
                // ramps up, then jumps back at the end of the cycle
                value = position + amplitude / 2 * (2 * this.getRamp(cyclePosition) - 1);
                break;
            case "reverse-sawtooth":
                // the sawtooth run backwards: jumps up at the beginning of the cycle, then ramps down
                value = position + amplitude / 2 * (2 * this.getRamp(1 - cyclePosition) - 1);
                break;
        }

        return Math.max(Math.min(value, 1), 0);
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
