package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer cached fixture capability.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
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

}
