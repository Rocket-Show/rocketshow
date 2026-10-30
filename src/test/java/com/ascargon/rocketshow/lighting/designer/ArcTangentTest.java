package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The arc tangent of fdlibm, which the designer works out the very same way (arc-tangent.ts)
 */
class ArcTangentTest {

    @Test
    void comesOutLikeTheStrictArcTangent() {
        Random random = new Random(7);

        for (int i = 0; i < 100000; i++) {
            double y = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(7) - 3);
            double x = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(7) - 3);

            assertEquals(StrictMath.atan2(y, x), ArcTangent.atan2(y, x), 0, y + ", " + x);
        }

        assertEquals(1.1857790291288564, ArcTangent.atan2(0.9267919362380059, 0.37557516814108993), 0);
    }

    @Test
    void takesEveryRangeOfNumbersAndTheAxes() {
        for (double x : new double[]{1e-12, 0.1, 0.5, 0.9, 1, 1.5, 3, 1e30}) {
            assertEquals(StrictMath.atan(x), ArcTangent.atan(x), 0);
            assertEquals(StrictMath.atan(-x), ArcTangent.atan(-x), 0);
        }

        assertEquals(0, ArcTangent.atan2(0, 1));
        assertEquals(Math.PI, ArcTangent.atan2(0, -1));
        assertEquals(-Math.PI, ArcTangent.atan2(-0.0, -1));
        assertEquals(Math.PI / 2, ArcTangent.atan2(1, 0));
        assertEquals(-Math.PI / 2, ArcTangent.atan2(-1, 0));
        assertEquals(StrictMath.atan2(-1, -1), ArcTangent.atan2(-1, -1), 0);
    }
}
