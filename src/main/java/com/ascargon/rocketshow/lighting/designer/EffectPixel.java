package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A pixel effect: a pattern painted over the pixels of the bars and matrices of a preset (and fixtures without pixels,
 * each of them one pixel), in the colors of a palette. Each pixel takes the color of the pattern where it is in its
 * fixture. It runs on the timing of a curve: its period, its phase, the chase from one fixture to the next (a fixture
 * with all its pixels takes one step) and how long it runs. The designer plays it the very same way (effect-pixel.ts).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectPixel extends EffectCurve implements PixelPatternSettings {

    // see PixelPatterns
    private String pattern = "chase";
    private double size = 0.25;
    private double softness = 0.3;
    private String direction = "right";
    private String shape = "circle";
    private boolean bounce = false;

    // where the random looking patterns start from, so two effects with the same pattern can look different (a number
    // like in the designer, which works it out with the index of each fixture before it cuts it to a whole one)
    private double seed = 1;

    // the pattern runs out from the middle of each fixture to both of its ends, mirrored
    private boolean mirror = false;

    // the shades of the color the preset puts on each pixel, unless a palette of its own is picked
    private PixelPalette palette = presetPalette();

    // 'fixture' paints the pattern over each fixture on its own
    private String spread = "fixture";

    // what it does with the color the preset puts on a pixel: 'replace' it, 'mask' it (the pattern only sets how bright
    // it is), 'add' to it or take the 'max' of both on each of red, green and blue
    private String blend = "replace";

    private static PixelPalette presetPalette() {
        PixelPalette palette = new PixelPalette();
        palette.setRef("preset");
        return palette;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    // a value missing, null or empty keeps the default, like in the designer
    public void setPattern(String pattern) {
        this.pattern = pattern == null || pattern.isEmpty() ? "chase" : pattern;
    }

    @Override
    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    @Override
    public double getSoftness() {
        return softness;
    }

    public void setSoftness(double softness) {
        this.softness = softness;
    }

    @Override
    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction == null || direction.isEmpty() ? "right" : direction;
    }

    @Override
    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape == null || shape.isEmpty() ? "circle" : shape;
    }

    @Override
    public boolean isBounce() {
        return bounce;
    }

    public void setBounce(boolean bounce) {
        this.bounce = bounce;
    }

    public double getSeed() {
        return seed;
    }

    public void setSeed(double seed) {
        this.seed = seed;
    }

    public boolean isMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public PixelPalette getPalette() {
        return palette;
    }

    public void setPalette(PixelPalette palette) {
        this.palette = palette == null ? presetPalette() : palette;
    }

    public String getSpread() {
        return spread;
    }

    public void setSpread(String spread) {
        this.spread = spread == null || spread.isEmpty() ? "fixture" : spread;
    }

    public String getBlend() {
        return blend;
    }

    public void setBlend(String blend) {
        this.blend = blend == null || blend.isEmpty() ? "replace" : blend;
    }

}
