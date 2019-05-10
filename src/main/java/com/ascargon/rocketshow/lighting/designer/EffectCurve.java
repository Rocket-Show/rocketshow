package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectCurve extends Effect {

    private long lengthMillis = 2500;
    private long phaseMillis = 0;
    private int amplitude = 100;
    private int position = 0;
    private int minValue = 0;
    private int maxValue = 255;
    private long phasingMillis = 0;

    public double getValueAtMillis(long timeMillis, Integer fixtureIndex) {
        // Calculate the offset for phasing
        int phasingIndex = 0;

        if(fixtureIndex != null) {
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

}
