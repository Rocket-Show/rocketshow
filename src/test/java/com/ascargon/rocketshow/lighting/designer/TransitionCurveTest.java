package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How fades and step transitions move a value. These mirror the specs of transition-curve in
 * the Rocket Show Designer.
 */
class TransitionCurveTest {

    @Test
    void passesALinearTransitionThroughUnchanged() {
        assertEquals(0.25, TransitionCurve.apply("linear", 0.25));
        assertEquals(0.5, TransitionCurve.apply("linear", 0.5));
    }

    @Test
    void clampsOutsideTheTransition() {
        assertEquals(0, TransitionCurve.apply("ease-in-out", -1));
        assertEquals(0, TransitionCurve.apply("ease-in-out", 0));
        assertEquals(1, TransitionCurve.apply("ease-in-out", 1));
        assertEquals(1, TransitionCurve.apply("ease-in-out", 2));
    }

    @Test
    void treatsAMissingPositionAsNotStarted() {
        assertEquals(0, TransitionCurve.apply("linear", Double.NaN));
    }

    @Test
    void startsSlowlyWhenEasingInAndQuicklyWhenEasingOut() {
        assertTrue(TransitionCurve.apply("ease-in", 0.5) < 0.5);
        assertTrue(TransitionCurve.apply("ease-out", 0.5) > 0.5);
    }

    @Test
    void isSymmetricAroundTheMiddleWhenEasingInAndOut() {
        assertEquals(0.5, TransitionCurve.apply("ease-in-out", 0.5), 1e-10);
        assertEquals(1, TransitionCurve.apply("ease-in-out", 0.25) + TransitionCurve.apply("ease-in-out", 0.75), 1e-10);
    }

    @Test
    void risesWithoutEverFallingBack() {
        for (String curveType : new String[]{"linear", "ease-in", "ease-out", "ease-in-out", "snap"}) {
            double previous = 0;

            for (double position = 0; position <= 1.0001; position += 0.05) {
                double value = TransitionCurve.apply(curveType, position);
                assertTrue(value >= previous, curveType + " at " + position);
                previous = value;
            }
        }
    }

    @Test
    void holdsTheOldValueForAWholeSnappingTransition() {
        assertEquals(0, TransitionCurve.apply("snap", 0.01));
        assertEquals(0, TransitionCurve.apply("snap", 0.99));
        assertEquals(1, TransitionCurve.apply("snap", 1));
    }

    @Test
    void fallsBackToLinearForACurveItDoesNotKnow() {
        assertEquals(0.4, TransitionCurve.apply("from-a-newer-designer", 0.4));
        assertEquals(0.4, TransitionCurve.apply(null, 0.4));
    }

}
