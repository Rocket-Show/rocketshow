package com.ascargon.rocketshow.lighting.designer;

public class FixtureCapabilityValue {

    // DMX value between 0 and 255
    private int value;

    private FixtureCapability.FixtureCapabilityType type;
    private FixtureCapability.FixtureCapabilityColor color;

    public FixtureCapabilityValue(int value, FixtureCapability.FixtureCapabilityType type, FixtureCapability.FixtureCapabilityColor color) {
        this.value = value;

        this.type = type;
        this.color = color;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
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
