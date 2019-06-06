package com.ascargon.rocketshow.lighting.designer;

public class FixtureCapabilityValue {

    // value between 0 and 1
    private double valuePercentage;
    private FixtureCapability.FixtureCapabilityType type;
    private FixtureCapability.FixtureCapabilityColor color;

    public FixtureCapabilityValue() {
    }

    public double getValuePercentage() {
        return valuePercentage;
    }

    public void setValuePercentage(double valuePercentage) {
        this.valuePercentage = valuePercentage;
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
