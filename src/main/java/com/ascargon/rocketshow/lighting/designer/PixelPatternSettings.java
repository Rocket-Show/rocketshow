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

    // where it runs to: 'right', 'left', 'out' (from the middle), 'in' (to the middle), 'up' and 'down'
    String getDirection();

    // the shape of the shapes: 'circle', 'square', 'diamond', 'star' or 'heart'
    String getShape();

    // whether a comet runs back and forth instead of starting over
    boolean isBounce();

}
