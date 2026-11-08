package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

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

    // pan and tilt: the shape of the fan the degrees are spread over, from the first fixture to the last one or
    // ('center') from the one in the middle out to both ends (see Fan)
    private String fanShape;

    // A value with a percentage fanned out over the fixtures of the preset: the value of each fixture by its place
    // along the fan (see Fan). The percentage is the one in between, which a player not knowing the fans plays. Red,
    // green and blue fanned with stops at the same places make up a gradient of colors.
    private List<FanStop> fan;
    private Double aimX;
    private Double aimY;
    private Double aimZ;

    // the blade of a framing shutter: Top, Right, Bottom or Left
    private String blade;

    // the strobe: the effect it flashes in (see CapabilityPositions), a plain strobe if none is given
    private String shutterEffect;

    // pan and tilt, while a preset travels from one step into the next: the value of the step it travels from and how
    // far it has come (between 0 and 1). Each moving head travels from where the one value points it to where the
    // other does, so the heads fan in and out, follow a spot moving over the stage and turn from a spot to a position
    // alike (see PanTilt). Only the values a preset shows between two of its steps hold this, it is never saved.
    @JsonIgnore
    private FixtureCapabilityValue transitionFrom;
    @JsonIgnore
    private double transitionPosition;

    // a copy of the value, with all it says
    public FixtureCapabilityValue copy() {
        FixtureCapabilityValue copy = new FixtureCapabilityValue();
        copy.type = type;
        copy.profileUuid = profileUuid;
        copy.valuePercentage = valuePercentage;
        copy.color = color;
        copy.slotNumber = slotNumber;
        copy.wheel = wheel;
        copy.valueDegrees = valueDegrees;
        copy.spreadDegrees = spreadDegrees;
        copy.aimX = aimX;
        copy.aimY = aimY;
        copy.aimZ = aimZ;
        copy.blade = blade;
        copy.shutterEffect = shutterEffect;
        copy.fanShape = fanShape;
        copy.fan = fan == null ? null : new ArrayList<>(fan);
        copy.transitionFrom = transitionFrom;
        copy.transitionPosition = transitionPosition;
        return copy;
    }

    public String getFanShape() {
        return fanShape;
    }

    public void setFanShape(String fanShape) {
        this.fanShape = fanShape;
    }

    public List<FanStop> getFan() {
        return fan;
    }

    // a fan without any stops is no fan, like in the designer
    public void setFan(List<FanStop> fan) {
        this.fan = fan == null || fan.isEmpty() ? null : fan;
    }

    @JsonIgnore
    public FixtureCapabilityValue getTransitionFrom() {
        return transitionFrom;
    }

    @JsonIgnore
    public void setTransitionFrom(FixtureCapabilityValue transitionFrom) {
        this.transitionFrom = transitionFrom;
    }

    @JsonIgnore
    public double getTransitionPosition() {
        return transitionPosition;
    }

    @JsonIgnore
    public void setTransitionPosition(double transitionPosition) {
        this.transitionPosition = transitionPosition;
    }

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

    public String getShutterEffect() {
        return shutterEffect;
    }

    public void setShutterEffect(String shutterEffect) {
        this.shutterEffect = shutterEffect;
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
