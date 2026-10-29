package com.ascargon.rocketshow.lighting.designer;

/**
 * The sine over a cycle, sin(2π · cyclePosition), worked out the very same way as cycleSine of the designer: Math.sin
 * of the browser and the ones of Java round differently in the last digit, which would let the curves of the designer
 * and the ones on stage drift apart. The cycle is reduced to a quarter around its nearest multiple of 90° and the sine
 * or the cosine of what is left is taken from the polynomials of fdlibm (__kernel_sin, __kernel_cos), with nothing
 * but additions and multiplications, which give the same doubles on both sides.
 *
 * @author Moritz A. Vieli
 */
final class CycleSine {

    private static final double S1 = -1.66666666666666324348e-01;
    private static final double S2 = 8.33333333332248946124e-03;
    private static final double S3 = -1.98412698298579493134e-04;
    private static final double S4 = 2.75573137070700676789e-06;
    private static final double S5 = -2.50507602534068634195e-08;
    private static final double S6 = 1.58969099521155010221e-10;

    private static final double C1 = 4.16666666666666019037e-02;
    private static final double C2 = -1.38888888888741095749e-03;
    private static final double C3 = 2.48015872894767294178e-05;
    private static final double C4 = -2.75573143513906633035e-07;
    private static final double C5 = 2.08757232129817482790e-09;
    private static final double C6 = -1.13596475577881948265e-11;

    private CycleSine() {
    }

    // the sine of an angle between -π/4 and π/4
    private static double sine(double angle, double squared) {
        double cubed = squared * angle;
        double rest = S2 + squared * (S3 + squared * (S4 + squared * (S5 + squared * S6)));
        return angle + cubed * (S1 + squared * rest);
    }

    // the cosine of an angle between -π/4 and π/4
    private static double cosine(double squared) {
        double rest = squared * (C1 + squared * (C2 + squared * (C3 + squared * (C4 + squared * (C5 + squared * C6)))));
        return 1 - (0.5 * squared - squared * rest);
    }

    static double sin(double cyclePosition) {
        // the nearest quarter of the cycle and the angle left from it
        double quarter = Math.floor(cyclePosition * 4 + 0.5);
        double angle = 2 * Math.PI * (cyclePosition - quarter / 4);
        double squared = angle * angle;

        return switch ((int) (((quarter % 4) + 4) % 4)) {
            case 0 -> sine(angle, squared);
            case 1 -> cosine(squared);
            case 2 -> -sine(angle, squared);
            default -> -cosine(squared);
        };
    }

}
