package com.ascargon.rocketshow.lighting.designer;

/**
 * A value of a fan at its place along it, from 0 (the first fixture) to 1 (the last one), see Fan. The designer writes
 * it the very same way (fan-stop.ts).
 */
public class FanStop {

    private double position = 0;
    private double valuePercentage = 0;

    // a stop at the passed place with the passed value (not a constructor, which would be taken to read a stop of a
    // project, and fail on a value missing there)
    public static FanStop of(double position, double valuePercentage) {
        FanStop stop = new FanStop();
        stop.position = position;
        stop.valuePercentage = valuePercentage;
        return stop;
    }

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
