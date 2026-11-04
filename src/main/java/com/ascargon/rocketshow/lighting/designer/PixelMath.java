package com.ascargon.rocketshow.lighting.designer;

/**
 * The arithmetic of the pixel effects, worked out the very same way as pixel-math of the designer: nothing but the
 * basic operations, Math.floor, Math.abs, Math.min, Math.max and Math.sqrt (exact on both sides), CycleSine and
 * ArcTangent, in the same order, so Java and the browser come to the very same doubles. What looks random comes from a
 * hash of whole numbers, never from a random source: a moment of an effect always looks the same, in the preview of
 * the designer and on stage.
 *
 * @author Moritz A. Vieli
 */
final class PixelMath {

    private PixelMath() {
    }

    static double clamp01(double value) {
        return Math.max(Math.min(value, 1), 0);
    }

    // the part of a number after its whole cycles, between 0 and 1 (also for a negative one)
    static double fract(double value) {
        return value - Math.floor(value);
    }

    // up from 0 to 1 over the first half of each cycle, back down over the second
    static double triangle(double value) {
        return 1 - Math.abs(2 * fract(value) - 1);
    }

    // 0 below from, 1 above to and a smooth step in between. A step of no width jumps at from.
    static double smoothStep(double from, double to, double value) {
        if (!(to > from)) {
            return value < from ? 0 : 1;
        }

        double t = clamp01((value - from) / (to - from));

        return t * t * (3 - 2 * t);
    }

    // 1 within halfWidth of a point, 0 beyond, softened over edge on both sides of the border
    static double band(double distance, double halfWidth, double edge) {
        return 1 - smoothStep(halfWidth - edge, halfWidth + edge, distance);
    }

    // a whole number as a 32 bit integer, cut off and wrapped around like the designer does it (value | 0)
    static int toInt(double value) {
        return (int) (long) value;
    }

    // A number between 0 and 1 which looks random, the same for the same three whole numbers (a pixel, a moment and
    // what it is for). The multiplications wrap around at 32 bits, like Math.imul of the designer.
    static double hash(int a, int b, int c) {
        int h = a * 374761393 + b * 668265263 + c * 1440670441;
        h = (h ^ (h >>> 15)) * 0x85EBCA77;
        h = (h ^ (h >>> 13)) * 0xC2B2AE35;
        h ^= h >>> 16;

        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    // a smooth landscape of values between 0 and 1: random heights on the whole numbers, smoothly blended in between
    static double valueNoise(double x, double y) {
        double xFloor = Math.floor(x);
        double yFloor = Math.floor(y);
        int xi = toInt(xFloor);
        int yi = toInt(yFloor);
        double xf = x - xFloor;
        double yf = y - yFloor;
        double u = xf * xf * (3 - 2 * xf);
        double v = yf * yf * (3 - 2 * yf);
        double a = hash(xi, yi, 7);
        double b = hash(xi + 1, yi, 7);
        double c = hash(xi, yi + 1, 7);
        double d = hash(xi + 1, yi + 1, 7);

        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    // the landscape with finer ones laid over it, for the look of flames and clouds
    static double fractalNoise(double x, double y) {
        return valueNoise(x, y) * 0.55 + valueNoise(x * 2.03 + 17.1, y * 2.03 + 5.3) * 0.3 + valueNoise(x * 4.1 + 3.7, y * 4.1 + 11.9) * 0.15;
    }

}
