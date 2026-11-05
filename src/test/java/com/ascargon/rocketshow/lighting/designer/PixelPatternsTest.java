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

        assertEquals(List.of("#ff8000", "#ffdfbf"), shades.stream().map(PixelPaletteStop::getColor).toList());
    }

    // a pattern with its settings, the others at their defaults
    private static PixelPatternSettings settings(String pattern, String direction) {
        EffectPixel effect = new EffectPixel();
        effect.setPattern(pattern);
        effect.setSize(0.2);
        effect.setSoftness(0);
        effect.setDirection(direction);
        return effect;
    }

    // how bright each pixel of a bar is at a moment
    private static double[] levels(PixelPatternSettings settings, double cycle, int columns) {
        double[] levels = new double[columns];
        double[] result = new double[2];

        for (int column = 0; column < columns; column++) {
            PixelContext context = new PixelContext();
            context.x = (column + 0.5) / columns;
            context.column = column;
            context.columns = columns;
            context.aspectY = 1.0 / columns;
            PixelPatterns.sample(settings, cycle, context, result);
            levels[column] = result[1];
        }

        return levels;
    }

    @Test
    void stacksThePixelsAtTheFarEndUntilAllOfThemAreThere() {
        // the first one piled up at the far end, the second one dropping, then all of them
        assertArrayEquals(new double[]{0, 1, 0, 1}, levels(settings("stack", "right"), 0.3, 4));
        assertArrayEquals(new double[]{1, 0, 1, 0}, levels(settings("stack", "left"), 0.3, 4));
        assertArrayEquals(new double[]{1, 1, 1, 1}, levels(settings("stack", "right"), 0.9, 4));
    }

    @Test
    void runsPulsesFromTheMiddleOutToTheEndsOfABar() {
        double[] early = levels(settings("center", "out"), 0.1, 10);
        double[] late = levels(settings("center", "out"), 0.9, 10);

        assertTrue(early[4] > early[0]);
        assertTrue(late[0] > late[4]);
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
