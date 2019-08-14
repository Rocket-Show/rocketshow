package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectCurve extends Effect {

    private List<FixtureCapability> capabilities = new ArrayList<>();
    private List<EffectCurveProfileChannels> channels = new ArrayList<>();

    private long lengthMillis = 2500;
    private long phaseMillis = 0;
    private int amplitude = 100;
    private int position = 0;
    private int minValue = 0;
    private int maxValue = 255;
    private long phasingMillis = 0;

    @Override
    public double getValueAtMillis(long timeMillis, Integer fixtureIndex) {
        // Calculate the offset for phasing
        int phasingIndex = 0;

        if (fixtureIndex != null) {
            phasingIndex = fixtureIndex;
        }

        double phase = this.phaseMillis + phasingIndex * this.phasingMillis;

        // Calculate the value according to the curve
        double value = this.amplitude / 2d * Math.sin((2 * Math.PI * (timeMillis - phase) / this.lengthMillis)) + 255 / 2d + this.position;

        if (value < this.minValue) {
            value = this.minValue;
        }

        if (value > this.maxValue) {
            value = this.maxValue;
        }

        return value;
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

    public int getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(int amplitude) {
        this.amplitude = amplitude;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        this.minValue = minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }

    public long getPhasingMillis() {
        return phasingMillis;
    }

    public void setPhasingMillis(long phasingMillis) {
        this.phasingMillis = phasingMillis;
    }
}
