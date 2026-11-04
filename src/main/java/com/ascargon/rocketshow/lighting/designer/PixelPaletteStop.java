package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A color of the palette of a pixel effect, at its place between 0 and 1.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PixelPaletteStop {

    private double position = 0;
    private String color = "#000000";

    // (not a constructor: Jackson would read the stops of a project with it, instead of keeping the defaults of the
    // values which are null)
    static PixelPaletteStop of(double position, String color) {
        PixelPaletteStop stop = new PixelPaletteStop();
        stop.position = position;
        stop.color = color;
        return stop;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

}
