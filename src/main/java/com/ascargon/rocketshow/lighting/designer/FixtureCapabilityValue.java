package com.ascargon.rocketshow.lighting.designer;

public class FixtureCapabilityValue {

    // value between 0 and 1
    private Double valuePercentage;
    private FixtureCapability.FixtureCapabilityType type;
    private FixtureCapability.FixtureCapabilityColor color;
    Integer slotNumber;
    String wheel;
    String fixtureTemplateUuid;

    public FixtureCapabilityValue() {
    }

    public Double getValuePercentage() {
        return valuePercentage;
    }

    public void setValuePercentage(Double valuePercentage) {
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

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public String getWheel() {
        return wheel;
    }

    public void setWheel(String wheel) {
        this.wheel = wheel;
    }

    public String getFixtureTemplateUuid() {
        return fixtureTemplateUuid;
    }

    public void setFixtureTemplateUuid(String fixtureTemplateUuid) {
        this.fixtureTemplateUuid = fixtureTemplateUuid;
    }
}
