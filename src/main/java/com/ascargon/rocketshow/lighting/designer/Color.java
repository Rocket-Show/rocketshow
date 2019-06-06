package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer color.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Color {

    private Double red;
    private Double green;
    private Double blue;

    public Color(Double red, Double green, Double blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public Color() { }

    public Double getRed() {
        return red;
    }

    public void setRed(Double red) {
        this.red = red;
    }

    public Double getGreen() {
        return green;
    }

    public void setGreen(Double green) {
        this.green = green;
    }

    public Double getBlue() {
        return blue;
    }

    public void setBlue(Double blue) {
        this.blue = blue;
    }
}
