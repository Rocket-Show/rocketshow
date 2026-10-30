package com.ascargon.rocketshow.lighting.designer;

public class FixtureCapabilityValue {

    private FixtureCapability.FixtureCapabilityType type;
    private String profileUuid;

    // value between 0 and 1
    private Double valuePercentage;

    private FixtureCapability.FixtureCapabilityColor color;
    private Double slotNumber;
    private String wheel;

    // pan and tilt: the position in degrees from the middle of the range, the degrees the fixtures are fanned out
    // over and the spot on the stage (in cm) they point at (see PanTilt)
    private Double valueDegrees;
    private Double spreadDegrees;
    private Double aimX;
    private Double aimY;
    private Double aimZ;

    // the blade of a framing shutter: Top, Right, Bottom or Left
    private String blade;

    public Double getValueDegrees() {
        return valueDegrees;
    }

    public void setValueDegrees(Double valueDegrees) {
        this.valueDegrees = valueDegrees;
    }

    public Double getSpreadDegrees() {
        return spreadDegrees;
    }

    public void setSpreadDegrees(Double spreadDegrees) {
        this.spreadDegrees = spreadDegrees;
    }

    public Double getAimX() {
        return aimX;
    }

    public void setAimX(Double aimX) {
        this.aimX = aimX;
    }

    public Double getAimY() {
        return aimY;
    }

    public void setAimY(Double aimY) {
        this.aimY = aimY;
    }

    public Double getAimZ() {
        return aimZ;
    }

    public void setAimZ(Double aimZ) {
        this.aimZ = aimZ;
    }

    public String getBlade() {
        return blade;
    }

    public void setBlade(String blade) {
        this.blade = blade;
    }

    public FixtureCapability.FixtureCapabilityType getType() {
        return type;
    }

    public void setType(FixtureCapability.FixtureCapabilityType type) {
        this.type = type;
    }

    public String getProfileUuid() {
        return profileUuid;
    }

    public void setProfileUuid(String profileUuid) {
        this.profileUuid = profileUuid;
    }

    public Double getValuePercentage() {
        return valuePercentage;
    }

    public void setValuePercentage(Double valuePercentage) {
        this.valuePercentage = valuePercentage;
    }

    public FixtureCapability.FixtureCapabilityColor getColor() {
        return color;
    }

    public void setColor(FixtureCapability.FixtureCapabilityColor color) {
        this.color = color;
    }

    public Double getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Double slotNumber) {
        this.slotNumber = slotNumber;
    }

    public String getWheel() {
        return wheel;
    }

    public void setWheel(String wheel) {
        this.wheel = wheel;
    }
}
