package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The values a preset applies at one point in time: either the values of a single step
 * or, while a transition is running, the ones interpolated between two of them. The
 * lists may be the ones of a step itself, so a state is only ever read from.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
public class PresetStepState {

    private List<FixtureChannelValue> fixtureChannelValues = new ArrayList<>();
    private List<FixtureCapabilityValue> fixtureCapabilityValues = new ArrayList<>();

    // how much of each effect of the preset is let through, by effect uuid. Effects
    // missing from the map run fully.
    private Map<String, Double> effectAmounts = new HashMap<>();

    public double getEffectAmount(String effectUuid) {
        Double amount = effectAmounts.get(effectUuid);

        return amount == null ? 1 : amount;
    }

}
