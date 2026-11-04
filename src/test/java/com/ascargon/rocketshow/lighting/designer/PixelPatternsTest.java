package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The patterns and palettes of the pixel effects. That they come to the values of the designer is checked by the
 * conformance cases (pixel-pattern.json), these check what the cases leave out.
 */
class PixelPatternsTest {

    @Test
    void readsAPaletteWithValuesWhichAreNullOrMissingLikeTheDesigner() {
        PixelPalette palette = DesignerJson.MAPPER.readValue("{\"stops\": [{\"position\": null, \"color\": null}, {}], \"between\": null}", PixelPalette.class);

        assertEquals("smooth", palette.getBetween());
        assertEquals("mirror", palette.getRepeat());
        assertEquals(0, palette.getStops().getFirst().getPosition());
        assertEquals("#000000", palette.getStops().getFirst().getColor());
        assertArrayEquals(new double[]{0, 0, 0}, new PixelPalette().colorAt(0.5));
    }

    @Test
    void makesTheShadesOfAColorLikeTheDesigner() {
        List<PixelPaletteStop> shades = PixelPalette.shadesOf(new double[]{1, 0.5, 0});

        assertEquals(List.of("#000000", "#ff8000", "#ffdfbf"), shades.stream().map(PixelPaletteStop::getColor).toList());
    }

    @Test
    void cutsANumberTo32BitsLikeTheDesigner() {
        assertEquals(3, PixelMath.toInt(3.9));
        assertEquals(-3, PixelMath.toInt(-3.9));
        assertEquals(5, PixelMath.toInt(Math.pow(2, 32) + 5));
    }

    @Test
    void hashesWholeNumbersToNumbersBetween0And1() {
        for (int i = -500; i < 500; i++) {
            double value = PixelMath.hash(i, i * 3, 7);
            assertTrue(value >= 0 && value < 1, String.valueOf(value));
        }
    }

}
