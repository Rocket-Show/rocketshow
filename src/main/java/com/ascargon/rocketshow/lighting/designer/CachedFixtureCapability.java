package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer cached fixture capability.
 *
 * @author Moritz A. Vieli
 */
public class CachedFixtureCapability {

    private FixtureCapability capability;

    // the wheel names, if available
    private String wheelName;

    // the wheel, if available
    private FixtureWheel wheel;

    // the wheel slots, if available
    private List<FixtureWheelSlot> wheelSlots = new ArrayList<>();

    // is this a color wheel?
    private boolean wheelIsColor = false;

    private double centerValue;

    public FixtureCapability getCapability() {
        return capability;
    }

    public void setCapability(FixtureCapability capability) {
        this.capability = capability;
    }

    public String getWheelName() {
        return wheelName;
    }

    public void setWheelName(String wheelName) {
        this.wheelName = wheelName;
    }

    public FixtureWheel getWheel() {
        return wheel;
    }

    public void setWheel(FixtureWheel wheel) {
        this.wheel = wheel;
    }

    public List<FixtureWheelSlot> getWheelSlots() {
        return wheelSlots;
    }

    public void setWheelSlots(List<FixtureWheelSlot> wheelSlots) {
        this.wheelSlots = wheelSlots;
    }

    public boolean isWheelIsColor() {
        return wheelIsColor;
    }

    public void setWheelIsColor(boolean wheelIsColor) {
        this.wheelIsColor = wheelIsColor;
    }

    public double getCenterValue() {
        return centerValue;
    }

    public void setCenterValue(double centerValue) {
        this.centerValue = centerValue;
    }
}
