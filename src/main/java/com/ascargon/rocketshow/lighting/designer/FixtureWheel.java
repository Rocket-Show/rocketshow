package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer fixture wheel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureWheel {

    private List<FixtureWheelSlot> slots = new ArrayList<>();

    public List<FixtureWheelSlot> getSlots() {
        return slots;
    }

    public void setSlots(List<FixtureWheelSlot> slots) {
        this.slots = slots;
    }
}
