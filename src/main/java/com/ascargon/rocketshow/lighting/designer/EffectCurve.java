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

    // curve types the duty cycle applies to. the sawtooths are the limits of the triangle
    // (rising over the whole cycle and falling over it), so a width would only turn them
    // into a triangle again.
    private final static List<String> DUTY_CYCLE_TYPES = List.of("sine", "square", "triangle");

    // a cycle border is never hit exactly when the curve is sampled, so the value held
    // after a run is taken just before it
    private final static double CYCLE_EPSILON = 1e-9;

    private String curveType = "sine";

    private List<FixtureCapability> capabilities = new ArrayList<>();
    private List<EffectCurveProfileChannels> channels = new ArrayList<>();

    private long lengthMillis = 2500;
    private long phaseMillis = 0;
    private float amplitude = 1;
    private float position = 0.5f;

    // the part of the period the curve is on (square) or rising (sine, triangle), between
    // 0 and 1. 0.5 is the symmetric curve. a chase shows it as the part of the fixtures
    // that are on at the same time: 0.1 lights one fixture out of ten.
    private float dutyCycle = 0.5f;

    // how long the curve keeps running:
    // "infinite" never stops, "cycles" runs runCycles full periods, "duration" runs
    // runDurationMillis. the chase delays the run of each fixture, so all of them run
    // their cycles completely.
    private String runMode = "infinite";
    private int runCycles = 1;
    private long runDurationMillis = 5000;

    // what a curve that has finished running puts on its channels: "hold" keeps the last
    // value, "base" stops applying the effect, which leaves the fixtures with whatever
    // the rest of the preset puts on them
    private String endMode = "hold";

    // how the curve is shifted from one fixture to the next (chasing):
    // "millis" shifts it by a fixed time, "spread" distributes phasingCycles full
    // cycles over all fixtures of the preset, which keeps the chase intact when the
    // period or the number of fixtures changes.
    // both values are signed: a negative one chases in the opposite direction.
    private String phasingMode = "millis";
    private long phasingMillis = 0;
    private float phasingCycles = 1;

    // how many fixtures share the same chase step (1 = each fixture on its own)
    private int phasingGroupSize = 1;

    // the time the curve is shifted by for the passed fixture of the preset
    public double getPhasingMillis(Integer fixtureIndex, Integer fixtureCount) {
        int groupSize = Math.max(this.phasingGroupSize, 1);
        int step = (fixtureIndex == null ? 0 : fixtureIndex) / groupSize;

        if ("spread".equals(this.phasingMode)) {
            // distribute the cycles over all chase steps of the preset
            int count = fixtureCount == null ? 1 : fixtureCount;
            int steps = Math.max((int) Math.ceil((double) count / groupSize), 1);
            return (double) step / steps * this.phasingCycles * this.lengthMillis;
        }

        return (double) step * this.phasingMillis;
    }

    // whether the duty cycle changes anything on the current curve type
    public boolean hasDutyCycle() {
        return DUTY_CYCLE_TYPES.contains(this.curveType);
    }

    // how long the curve runs, measured from the moment its fixture starts, or null if it
    // never stops
    @JsonIgnore
    public Double getRunEndMillis() {
        if ("cycles".equals(this.runMode)) {
            return (double) Math.max(this.runCycles, 1) * this.lengthMillis;
        }

        if ("duration".equals(this.runMode)) {
            return (double) Math.max(this.runDurationMillis, 0);
        }

        return null;
    }

    // how long one pass of a curve that stops takes, from the moment the first fixture
    // starts until the last one has finished, plus a rest that shows what it leaves behind.
    // a preset that is not placed in a composition has no moment it starts at, so it is
    // previewed by repeating this pass. null for a curve that never stops.
    @JsonIgnore
    public Double getRunLoopMillis(Integer fixtureCount) {
        Double runEndMillis = this.getRunEndMillis();

        if (runEndMillis == null) {
            return null;
        }

        // the chase delays the fixtures against each other, so the pass is only over once
        // the last of them has run
        int count = fixtureCount == null ? 1 : fixtureCount;
        double chaseMillis = Math.abs(this.getPhasingMillis(Math.max(count - 1, 0), fixtureCount));

        return Math.max((runEndMillis + chaseMillis) * 1.25, 1);
    }

    // the value the curve puts on its channels, or null if it does not apply at this
    // moment - the fixtures keep whatever the rest of the preset puts on them then
    @Override
    public Double getValueAtMillis(long timeMillis, Integer fixtureIndex, Integer fixtureCount) {
        double phasingMillis = this.getPhasingMillis(fixtureIndex, fixtureCount);
        double phase = this.phaseMillis + phasingMillis;

        Double runEndMillis = this.getRunEndMillis();

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

                return this.getCurveValue(this.getCyclePosition(runEndMillis - this.phaseMillis, true));
            }
        }

        return this.getCurveValue(this.getCyclePosition(timeMillis - phase, false));
    }

    // the position inside the current cycle, between 0 and 1
    private double getCyclePosition(double shiftedMillis, boolean atRunEnd) {
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
        if (!this.hasDutyCycle()) {
            return 0.5;
        }

        return Math.max(Math.min(this.dutyCycle, 1), 0);
    }

    // remaps the position inside the cycle, so the curve rises over the duty cycle and
    // falls over the rest of it. the symmetric 50 % leaves it untouched.
    private double warpCyclePosition(double cyclePosition) {
        // an instant rise or fall would divide by zero, so the curve keeps a trace of both
        double duty = Math.max(Math.min(this.getClampedDutyCycle(), 0.999), 0.001);

        if (duty == 0.5) {
            return cyclePosition;
        }

        // measured from the trough of the curve, where it starts to rise
        double fromTrough = (cyclePosition + 0.25) % 1;
        double warped = fromTrough < duty ? fromTrough / duty * 0.5 : 0.5 + (fromTrough - duty) / (1 - duty) * 0.5;

        return (warped + 0.75) % 1;
    }

    // the value of the curve at a position inside its cycle, between 0 and 1
    private double getCurveValue(double cyclePosition) {
        // Calculate the value between 0 and 1 according to the curve
        double value = 0d;
        double warped;

        switch (this.curveType) {
            case "sine":
                warped = this.warpCyclePosition(cyclePosition);
                value = position + amplitude / 2 * Math.sin(2 * Math.PI * warped) / 2d;
                break;
            case "square":
                // the curve is on over the last part of the cycle, which leaves the default
                // of 50 % the same square as before there was a duty cycle
                if (cyclePosition >= 1 - this.getClampedDutyCycle()) {
                    value = position + amplitude / 2;
                } else {
                    value = position - amplitude / 2;
                }
                break;
            case "triangle":
                // rises and falls linearly, in phase with the sine
                warped = this.warpCyclePosition(cyclePosition);
                value = position + amplitude / 2 * (1 - 4 * Math.abs((warped + 0.25) % 1 - 0.5)) / 2d;
                break;
            case "sawtooth":
                // ramps up over the whole cycle, then jumps back
                value = position + amplitude / 2 * (2 * cyclePosition - 1) / 2d;
                break;
            case "reverse-sawtooth":
                // ramps down over the whole cycle, then jumps back
                value = position + amplitude / 2 * (1 - 2 * cyclePosition) / 2d;
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

    public long getLengthMillis() {
        return lengthMillis;
    }

    public void setLengthMillis(long lengthMillis) {
        this.lengthMillis = lengthMillis;
    }

    public long getPhaseMillis() {
        return phaseMillis;
    }

    public void setPhaseMillis(long phaseMillis) {
        this.phaseMillis = phaseMillis;
    }

    public float getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(float amplitude) {
        this.amplitude = amplitude;
    }

    public float getPosition() {
        return position;
    }

    public void setPosition(float position) {
        this.position = position;
    }

    public long getPhasingMillis() {
        return phasingMillis;
    }

    public void setPhasingMillis(long phasingMillis) {
        this.phasingMillis = phasingMillis;
    }

    public String getPhasingMode() {
        return phasingMode;
    }

    public void setPhasingMode(String phasingMode) {
        this.phasingMode = phasingMode;
    }

    public float getPhasingCycles() {
        return phasingCycles;
    }

    public void setPhasingCycles(float phasingCycles) {
        this.phasingCycles = phasingCycles;
    }

    public int getPhasingGroupSize() {
        return phasingGroupSize;
    }

    public void setPhasingGroupSize(int phasingGroupSize) {
        this.phasingGroupSize = phasingGroupSize;
    }

    public float getDutyCycle() {
        return dutyCycle;
    }

    public void setDutyCycle(float dutyCycle) {
        this.dutyCycle = dutyCycle;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public int getRunCycles() {
        return runCycles;
    }

    public void setRunCycles(int runCycles) {
        this.runCycles = runCycles;
    }

    public long getRunDurationMillis() {
        return runDurationMillis;
    }

    public void setRunDurationMillis(long runDurationMillis) {
        this.runDurationMillis = runDurationMillis;
    }

    public String getEndMode() {
        return endMode;
    }

    public void setEndMode(String endMode) {
        this.endMode = endMode;
    }
}
