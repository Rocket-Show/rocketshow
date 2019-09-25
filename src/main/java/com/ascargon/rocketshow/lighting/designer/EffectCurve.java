package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.composition.CompositionPlayer;
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
    private long phasingMillis = 0;

    @Override
    public double getValueAtMillis(long timeMillis, Integer fixtureIndex) {
        // Calculate the offset for phasing
        int phasingIndex = 0;

        if (fixtureIndex != null) {
            phasingIndex = fixtureIndex;
        }

        double phase = this.phaseMillis + phasingIndex * this.phasingMillis;

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
                // TODO
                break;
            case "sawtooth":
                // TODO
                break;
            case "reverse-sawtooth":
                // TODO
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
}
