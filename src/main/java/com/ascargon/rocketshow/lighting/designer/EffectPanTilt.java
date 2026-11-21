package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A movement of the moving heads of a preset around where the presets of the scene point them, whichever preset does
 * it: a circle, a figure 8, a line back and forth or a wave (back and forth on the pan, nodding three times on the tilt
 * on the way), its size given in degrees on each axis, which moves every model the same way. It runs on the timing of
 * a curve: its period, its phase, the chase over the fixtures, how long it runs and how far it moves each fixture by
 * its place in the fan of the preset (a line fanned "linear" opens and closes the fan of the heads, see
 * EffectCurve.fanMode). The lighting engine plays it, the very same way as the designer.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectPanTilt extends EffectCurve {

    private String shape = "circle";

    // how far the movement reaches from one side to the other, in degrees
    private double sizePanDegrees = 60;
    private double sizeTiltDegrees = 30;

    public String getShape() {
        return shape;
    }

    // a value missing or null keeps the default, like in the designer
    public void setShape(String shape) {
        this.shape = shape == null || shape.isEmpty() ? "circle" : shape;
    }


    public double getSizePanDegrees() {
        return sizePanDegrees;
    }

    public void setSizePanDegrees(Double sizePanDegrees) {
        this.sizePanDegrees = sizePanDegrees == null ? 60 : sizePanDegrees;
    }

    public double getSizeTiltDegrees() {
        return sizeTiltDegrees;
    }

    public void setSizeTiltDegrees(Double sizeTiltDegrees) {
        this.sizeTiltDegrees = sizeTiltDegrees == null ? 30 : sizeTiltDegrees;
    }
}
