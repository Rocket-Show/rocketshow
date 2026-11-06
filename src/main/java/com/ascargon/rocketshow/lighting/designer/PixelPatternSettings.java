package com.ascargon.rocketshow.lighting.designer;

/**
 * What a pixel effect sets for its pattern (see PixelPatterns and pixel-pattern.ts of the designer).
 *
 * @author Moritz A. Vieli
 */
interface PixelPatternSettings {

    String getPattern();

    // how big the pattern is (a block, a tail, rings, ...), between 0 and 1. What it means depends on the pattern.
    double getSize();

    // how soft its edges are, between 0 (hard) and 1
    double getSoftness();

    // where it runs to: 'right', 'left', 'out' (from the middle), 'in' (to the middle), 'up' and 'down', or which way
    // it turns: 'cw' (clockwise, the default) and 'ccw'
    String getDirection();

    // the shape of the shapes and the tunnel: 'circle', 'square', 'diamond', 'star' or 'heart'. The path of the orbit:
    // 'circle', 'figure8', 'line' or 'wave', and the curve of the wave: 'sine', 'triangle', 'square' or 'sawtooth'.
    String getShape();

    // whether a comet runs back and forth instead of starting over
    boolean isBounce();

    // the picture of a text or an image (see PixelBitmap), and how it is shown: how it scrolls ('left', 'right', 'up',
    // 'down' or 'none', 'auto' for the way of the pattern: a text to the left, an image not), where a text which does
    // not scroll is aligned ('left', 'center' or 'right'), how many pixels a text is moved up, how an image is fitted
    // into the pixels ('contain', 'cover', 'stretch' or 'pixels', one of the image for each) and their colors: of a
    // text 'along' it, of its 'letters' or 'one', of an image its 'own' or its brightness through the 'palette'
    default PixelBitmap getBitmap() {
        return null;
    }

    default String getScroll() {
        return "auto";
    }

    default String getAlign() {
        return "center";
    }

    default double getOffsetY() {
        return 0;
    }

    default String getFit() {
        return "contain";
    }

    default String getTextColors() {
        return "along";
    }

    default String getImageColors() {
        return "own";
    }

}
