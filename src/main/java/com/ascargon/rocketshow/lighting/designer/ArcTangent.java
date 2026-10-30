package com.ascargon.rocketshow.lighting.designer;

/**
 * The arc tangent, worked out the very same way as arc-tangent.ts of the designer: Math.atan2 of the browser and
 * StrictMath here round differently in the last digit, which would point the moving heads aimed at a spot of the
 * designer and the ones on stage a tiny bit apart. These are the polynomials of fdlibm (s_atan.c, e_atan2.c), with
 * nothing but additions, multiplications and divisions, which give the same doubles on both sides.
 */
final class ArcTangent {

    private static final double[] ATAN_HI = {4.63647609000806093515e-1, 7.85398163397448278999e-1, 9.82793723247329054082e-1, 1.570796326794896558e0};
    private static final double[] ATAN_LO = {2.26987774529616870924e-17, 3.06161699786838301793e-17, 1.39033110312309984516e-17, 6.12323399573676603587e-17};

    private static final double[] AT = {
            3.33333333333329318027e-1, -1.99999999998764832476e-1, 1.42857142725034663711e-1, -1.1111110405462355788e-1,
            9.09088713343650656196e-2, -7.69187620504482999495e-2, 6.66107313738753120669e-2, -5.83357013379057348645e-2,
            4.97687799461593236017e-2, -3.6531572744216915527e-2, 1.62858201153657823623e-2
    };

    private static final double PI = 3.1415926535897931160e0;
    private static final double PI_LO = 1.2246467991473531772e-16;

    private ArcTangent() {
    }

    // the arc tangent of a number, between -π/2 and π/2
    static double atan(double value) {
        double sign = value < 0 ? -1 : 1;
        double x = Math.abs(value);
        int id;

        if (x >= 7.378697629483820646e19) {
            // beyond 2^66
            return sign * (ATAN_HI[3] + ATAN_LO[3]);
        }

        if (x < 0.4375) {
            if (x < 1.862645149230957e-9) {
                // below 2^-29
                return value;
            }

            id = -1;
        } else if (x < 1.1875) {
            if (x < 0.6875) {
                id = 0;
                x = (2 * x - 1) / (2 + x);
            } else {
                id = 1;
                x = (x - 1) / (x + 1);
            }
        } else if (x < 2.4375) {
            id = 2;
            x = (x - 1.5) / (1 + 1.5 * x);
        } else {
            id = 3;
            x = -1 / x;
        }

        double z = x * x;
        double w = z * z;
        double s1 = z * (AT[0] + w * (AT[2] + w * (AT[4] + w * (AT[6] + w * (AT[8] + w * AT[10])))));
        double s2 = w * (AT[1] + w * (AT[3] + w * (AT[5] + w * (AT[7] + w * AT[9]))));

        if (id < 0) {
            return value - value * (s1 + s2);
        }

        return sign * (ATAN_HI[id] - (x * (s1 + s2) - ATAN_LO[id] - x));
    }

    // the angle of the point (x, y) from the x axis, between -π and π, like Math.atan2
    static double atan2(double y, double x) {
        if (x == 1) {
            return atan(y);
        }

        if (y == 0) {
            return x < 0 ? (Double.doubleToRawLongBits(y) != 0 ? -PI : PI) : y;
        }

        if (x == 0) {
            return y < 0 ? -PI / 2 : PI / 2;
        }

        double z = atan(Math.abs(y / x));

        if (x > 0) {
            return y < 0 ? -z : z;
        }

        return y < 0 ? z - PI_LO - PI : PI - (z - PI_LO);
    }
}
