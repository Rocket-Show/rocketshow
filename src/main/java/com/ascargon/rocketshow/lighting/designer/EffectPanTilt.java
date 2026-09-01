package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectPanTilt extends Effect {

    public Double getValueAtMillis(long timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        // TODO
        return 0d;
    }

}
