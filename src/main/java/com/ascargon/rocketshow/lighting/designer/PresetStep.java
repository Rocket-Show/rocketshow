package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One state of a Rocket Show Designer preset. A preset holds at least one step and runs
 * through them over its playing time; a preset with a single step is a static look.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class PresetStep {

    private String uuid;

    // when this step is fully reached, relative to the start of the preset. The
    // transition runs over the time before it, so a step lands on its millisecond (and
    // with it on its beat) instead of only starting to move there.
    private long startMillis = 0;

    // how long the values travel from the previous step to this one: null = the whole
    // time since that step, 0 = a jump
    private Long transitionMillis;
    private String transitionCurve = "linear";

    // the selected values
    private List<FixtureChannelValue> fixtureChannelValues = new ArrayList<>();
    private List<FixtureCapabilityValue> fixtureCapabilityValues = new ArrayList<>();

    // how much of each effect of the preset this step lets through
    private List<PresetStepEffectAmount> effectAmounts = new ArrayList<>();

    // how much of the passed effect this step lets through (effects a step says nothing
    // about run fully, which is what a preset without any step settings did before)
    public double getEffectAmount(String effectUuid) {
        if (effectAmounts == null) {
            return 1;
        }

        for (PresetStepEffectAmount effectAmount : effectAmounts) {
            if (effectAmount.getEffectUuid() != null && effectAmount.getEffectUuid().equals(effectUuid)) {
                return effectAmount.getAmount();
            }
        }

        return 1;
    }

}
