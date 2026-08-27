package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * How much of one effect of a preset a step lets through. Effects stay on the preset so
 * they keep their phase across the steps: a step only opens or closes them.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class PresetStepEffectAmount {

    private String effectUuid;

    // 1 = the effect runs as it is set up, 0 = the step silences it
    private double amount = 1;

}
