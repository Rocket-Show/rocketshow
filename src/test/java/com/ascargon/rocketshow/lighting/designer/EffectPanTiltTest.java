package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Movements of the moving heads fanned over them by their place in the row, like effect-pan-tilt.spec.ts of the
 * designer (the conformance cases play them on whole fixtures).
 */
class EffectPanTiltTest {

    private static EffectPanTilt line(String fanMode, double sizePan, double sizeTilt) {
        EffectPanTilt movement = new EffectPanTilt();
        movement.setShape("line");
        movement.setSizePanDegrees(sizePan);
        movement.setSizeTiltDegrees(sizeTilt);
        movement.setFanMode(fanMode);
        return movement;
    }

    @Test
    void fansTheMovementToOppositeSides() {
        EffectPanTilt movement = line("linear", 90, 0);

        assertTrue(movement.isFanned());
        assertEquals(-1, movement.getFanFactor(0d));
        assertEquals(0, movement.getFanFactor(0.5));
        assertEquals(1, movement.getFanFactor(1d));

        // a quarter of the way: the first and the last head all the way to opposite sides, the one in the middle still
        assertEquals(-45, movement.getOffsetAtMillis(625, 0, 3, null).pan(), 1e-9);
        assertEquals(0, movement.getOffsetAtMillis(625, 1, 3, null).pan(), 1e-9);
        assertEquals(45, movement.getOffsetAtMillis(625, 2, 3, null).pan(), 1e-9);
        // three quarters: the other way round
        assertEquals(45, movement.getOffsetAtMillis(1875, 0, 3, null).pan(), 1e-9);
        assertEquals(-45, movement.getOffsetAtMillis(1875, 2, 3, null).pan(), 1e-9);

        // a single head can't be told apart from others: it stays where the step points it
        assertEquals(0, movement.getOffsetAtMillis(625, 0, 1, null).pan(), 1e-9);
        assertEquals(0, movement.getFanFactor(null));
    }

    @Test
    void fansTheMovementFromTheMiddleAlike() {
        EffectPanTilt movement = line("center", 90, 0);

        assertEquals(45, movement.getOffsetAtMillis(625, 0, 3, null).pan(), 1e-9);
        assertEquals(0, movement.getOffsetAtMillis(625, 1, 3, null).pan(), 1e-9);
        assertEquals(45, movement.getOffsetAtMillis(625, 2, 3, null).pan(), 1e-9);
    }

    @Test
    void fansTheMovementFromTheSide() {
        EffectPanTilt movement = line("side", 90, 40);

        assertEquals(0, movement.getFanFactor(0d));
        assertEquals(0.25, movement.getFanFactor(0.25));
        assertEquals(1, movement.getFanFactor(1d));
        assertEquals(20, movement.getOffsetAtMillis(625, 4, 5, null).tilt(), 1e-9);
        assertEquals(11.25, movement.getOffsetAtMillis(625, 1, 5, null).pan(), 1e-9);

        // its place on the stage, if the preset fans by it: NaN where the fixtures can't be told apart, which don't move
        assertEquals(45, movement.getOffsetAtMillis(625, 0, 5, null, 1d).pan(), 1e-9);
        assertEquals(0, movement.getOffsetAtMillis(625, 4, 5, null, Double.NaN).pan(), 1e-9);
    }

    @Test
    void movesAllTheHeadsTheSameWayUnlessItIsFanned() {
        EffectPanTilt movement = line(null, 60, 30);

        assertEquals("none", movement.getFanMode());
        assertFalse(movement.isFanned());
        assertEquals(1, movement.getFanFactor(0d));
        assertEquals(movement.getOffsetAtMillis(625, 0, 3, null), movement.getOffsetAtMillis(625, 2, 3, null));
    }

    @Test
    void readsHowAMovementIsFanned() {
        Effect fanned = new ObjectMapper().readValue("""
                {"type": "pan-tilt", "shape": "line", "fanMode": "linear"}
                """, Effect.class);
        assertEquals("linear", assertInstanceOf(EffectPanTilt.class, fanned).getFanMode());

        // a movement of a project before the fans moves all the heads the same way
        Effect old = new ObjectMapper().readValue("""
                {"type": "pan-tilt", "shape": "circle"}
                """, Effect.class);
        assertEquals("none", assertInstanceOf(EffectPanTilt.class, old).getFanMode());
    }
}
