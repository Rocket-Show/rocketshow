package com.ascargon.rocketshow.lighting.designer;

public class FixtureCapabilityValue {

    // DMX value between 0 and 255
    private double value;

    private FixtureCapability.FixtureCapabilityType type;
    private FixtureCapability.FixtureCapabilityColor color;

    public FixtureCapabilityValue(double value, FixtureCapability.FixtureCapabilityType type, FixtureCapability.FixtureCapabilityColor color) {
        this.value = value;

        this.type = type;
        this.color = color;
    }

    public FixtureCapabilityValue(double value, FixtureCapability.FixtureCapabilityType type) {
        this.value = value;

        this.type = type;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public FixtureCapability.FixtureCapabilityType getType() {
        return type;
    }

    public void setType(FixtureCapability.FixtureCapabilityType type) {
        this.type = type;
    }

    public FixtureCapability.FixtureCapabilityColor getColor() {
        return color;
    }

    public void setColor(FixtureCapability.FixtureCapabilityColor color) {
        this.color = color;
    }

}
