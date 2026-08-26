package com.ascargon.rocketshow.lighting.designer;

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

    private String curveType = "sine";

    private List<FixtureCapability> capabilities = new ArrayList<>();
    private List<EffectCurveProfileChannels> channels = new ArrayList<>();

    private long lengthMillis = 2500;
    private long phaseMillis = 0;
    private float amplitude = 1;
    private float position = 0.5f;

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

    @Override
    public double getValueAtMillis(long timeMillis, Integer fixtureIndex, Integer fixtureCount) {
        double phase = this.phaseMillis + this.getPhasingMillis(fixtureIndex, fixtureCount);

        // the position inside the current cycle, between 0 and 1
        double cyclePosition = (((timeMillis - phase) / lengthMillis) % 1 + 1) % 1;

        // Calculate the value between 0 and 1 according to the curve
        double value = 0d;

        switch (this.curveType) {
            case "sine":
                value = position + amplitude / 2 * Math.sin((2 * Math.PI * (timeMillis - phase) / lengthMillis)) / 2d;
                break;
            case "square":
                if (Math.signum(Math.sin((2 * Math.PI * (timeMillis - phase) / lengthMillis)) / 2) == -1.0) {
                    value = position + amplitude / 2;
                } else {
                    value = position - amplitude / 2;
                }
                break;
            case "triangle":
                // rises and falls linearly, in phase with the sine
                value = position + amplitude / 2 * (1 - 4 * Math.abs((cyclePosition + 0.25) % 1 - 0.5)) / 2d;
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
}
