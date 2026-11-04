package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The palette of a pixel effect: the colors a pattern gives its pixels, from the place it puts them at (see
 * PixelPatterns). The designer plays it the very same way (pixel-palette.ts).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixelPalette {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private List<PixelPaletteStop> stops = new ArrayList<>();

    // 'smooth' blends from one color to the next, 'steps' jumps from one to the next
    private String between = "smooth";

    // A pattern like the flow runs through the palette over and over. 'mirror' runs it back and forth, so a palette
    // which ends on another color than it starts with does not jump, 'wrap' starts over (for a closed one, a rainbow).
    private String repeat = "mirror";

    // which palette of the designer it was taken from, or 'preset' for the shades of the color the preset puts on each
    // pixel
    private String ref;

    // The color at a place of the palette, as red, green and blue between 0 and 1. The palette repeats beyond 0 and 1.
    public double[] colorAt(double place) {
        return colorOf(stops, between, repeat, place);
    }

    // the color at a place of the passed stops, which do not have to be sorted (see colorAt)
    static double[] colorOf(List<PixelPaletteStop> stops, String between, String repeat, double place) {
        if (stops == null || stops.isEmpty()) {
            return new double[]{0, 0, 0};
        }

        // by their place, the ones on the same place in the order they are listed in
        List<PixelPaletteStop> sorted = new ArrayList<>(stops);
        sorted.sort(Comparator.comparingDouble(PixelPaletteStop::getPosition));
        double position = "wrap".equals(repeat) ? PixelMath.fract(place) : PixelMath.triangle(place * 0.5);

        if ("steps".equals(between)) {
            return hexToRgb(sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(position * sorted.size()))).getColor());
        }

        if (position <= sorted.getFirst().getPosition()) {
            return hexToRgb(sorted.getFirst().getColor());
        }

        for (int i = 1; i < sorted.size(); i++) {
            if (position <= sorted.get(i).getPosition()) {
                double[] from = hexToRgb(sorted.get(i - 1).getColor());
                double[] to = hexToRgb(sorted.get(i).getColor());
                double width = sorted.get(i).getPosition() - sorted.get(i - 1).getPosition();
                double share = width > 0 ? (position - sorted.get(i - 1).getPosition()) / width : 1;

                return new double[]{from[0] + (to[0] - from[0]) * share, from[1] + (to[1] - from[1]) * share, from[2] + (to[2] - from[2]) * share};
            }
        }

        return hexToRgb(sorted.getLast().getColor());
    }

    // The shades of a color, for the palette which follows the color of the preset: from black up to the color and on to
    // most of the way to white. The color as red, green and blue between 0 and 1.
    static List<PixelPaletteStop> shadesOf(double[] color) {
        double[] light = new double[]{color[0] + (1 - color[0]) * 0.75, color[1] + (1 - color[1]) * 0.75, color[2] + (1 - color[2]) * 0.75};

        return List.of(PixelPaletteStop.of(0, "#000000"), PixelPaletteStop.of(0.62, rgbToHex(color)), PixelPaletteStop.of(1, rgbToHex(light)));
    }

    // a color like #ff8000 as red, green and blue between 0 and 1, black for anything else
    static double[] hexToRgb(String hex) {
        if (hex == null || !HEX_COLOR.matcher(hex).matches()) {
            return new double[]{0, 0, 0};
        }

        return new double[]{
                Integer.parseInt(hex.substring(1, 3), 16) / 255.0,
                Integer.parseInt(hex.substring(3, 5), 16) / 255.0,
                Integer.parseInt(hex.substring(5, 7), 16) / 255.0
        };
    }

    // red, green and blue between 0 and 1 as a color like #ff8000, rounded to the nearest of the 256 steps
    static String rgbToHex(double[] color) {
        StringBuilder hex = new StringBuilder("#");

        for (double component : color) {
            hex.append(String.format("%02x", Math.round(Math.max(Math.min(component, 1), 0) * 255)));
        }

        return hex.toString();
    }

    public List<PixelPaletteStop> getStops() {
        return stops;
    }

    public void setStops(List<PixelPaletteStop> stops) {
        this.stops = stops;
    }

    public String getBetween() {
        return between;
    }

    public void setBetween(String between) {
        this.between = between;
    }

    public String getRepeat() {
        return repeat;
    }

    public void setRepeat(String repeat) {
        this.repeat = repeat;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

}
