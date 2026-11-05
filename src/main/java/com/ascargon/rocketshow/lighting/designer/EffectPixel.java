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

    // 'fixture' paints the pattern over each fixture on its own, 'stage' spreads one picture over all of them, as they
    // hang on the stage: seen from the 'front' or from above ('top'), framed around the pixels of the preset (see
    // PixelStage)
    private String spread = "fixture";
    private String view = "front";

    // a pixel with a white LED lights the white part of the color (the least of red, green and blue) with it, the rest
    // with red, green and blue
    private boolean whiteLed = false;

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

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view == null || view.isEmpty() ? "front" : view;
    }

    public boolean isWhiteLed() {
        return whiteLed;
    }

    public void setWhiteLed(Boolean whiteLed) {
        this.whiteLed = Boolean.TRUE.equals(whiteLed);
    }

    // The picture of a text or an image and how it is shown (see PixelPatternSettings). The designer makes the picture
    // from the text or the image, this only reads the picture.
    private PixelBitmap bitmap;
    private String scroll = "auto";
    private String align = "center";
    private double offsetY = 0;
    private String fit = "contain";
    private String textColors = "along";
    private String imageColors = "own";

    @Override
    public PixelBitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(PixelBitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Override
    public String getScroll() {
        return scroll;
    }

    public void setScroll(String scroll) {
        this.scroll = scroll == null || scroll.isEmpty() ? "auto" : scroll;
    }

    @Override
    public String getAlign() {
        return align;
    }

    public void setAlign(String align) {
        this.align = align == null || align.isEmpty() ? "center" : align;
    }

    @Override
    public double getOffsetY() {
        return offsetY;
    }

    public void setOffsetY(Double offsetY) {
        this.offsetY = offsetY == null ? 0 : offsetY;
    }

    @Override
    public String getFit() {
        return fit;
    }

    public void setFit(String fit) {
        this.fit = fit == null || fit.isEmpty() ? "contain" : fit;
    }

    @Override
    public String getTextColors() {
        return textColors;
    }

    public void setTextColors(String textColors) {
        this.textColors = textColors == null || textColors.isEmpty() ? "along" : textColors;
    }

    @Override
    public String getImageColors() {
        return imageColors;
    }

    public void setImageColors(String imageColors) {
        this.imageColors = imageColors == null || imageColors.isEmpty() ? "own" : imageColors;
    }

    public String getBlend() {
        return blend;
    }

    public void setBlend(String blend) {
        this.blend = blend == null || blend.isEmpty() ? "replace" : blend;
    }

}
