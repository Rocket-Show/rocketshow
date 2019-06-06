package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer fixture wheel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureWheel {

    private FixtureWheelSlot[] slots;

    public FixtureWheelSlot[] getSlots() {
        return slots;
    }

    public void setSlots(FixtureWheelSlot[] slots) {
        this.slots = slots;
    }
}
