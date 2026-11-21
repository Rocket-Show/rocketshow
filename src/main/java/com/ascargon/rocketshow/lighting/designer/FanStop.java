package com.ascargon.rocketshow.lighting.designer;

/**
 * A value of a fan at its place along it, from 0 (the first fixture) to 1 (the last one). The designer writes it the
 * very same way (fan-stop.ts).
 */
public class FanStop {

    private double position = 0;
    private double valuePercentage = 0;

    public double getPosition() {
        return position;
    }

    // a value missing or null keeps the default, like in the designer
    public void setPosition(Double position) {
        this.position = position == null ? 0 : position;
    }

    public double getValuePercentage() {
        return valuePercentage;
    }

    public void setValuePercentage(Double valuePercentage) {
        this.valuePercentage = valuePercentage == null ? 0 : valuePercentage;
    }
}
