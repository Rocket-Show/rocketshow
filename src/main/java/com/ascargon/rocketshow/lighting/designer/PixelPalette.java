package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * The palette of a pixel effect: the colors a pattern gives its pixels, from the place it puts them at. The lighting
 * engine plays it the very same way as the designer (pixel-palette.ts).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixelPalette {

    private List<PixelPaletteStop> stops = new ArrayList<>();

    // 'smooth' blends from one color to the next, 'steps' jumps from one to the next
    private String between = "smooth";

    // A pattern like the flow runs through the palette over and over. 'mirror' runs it back and forth, so a palette
    // which ends on another color than it starts with does not jump, 'wrap' starts over (for a closed one, a rainbow).
    private String repeat = "mirror";

    // which palette of the designer it was taken from, or 'preset' for the shades of the color the preset puts on each
    // pixel
    private String ref;

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
