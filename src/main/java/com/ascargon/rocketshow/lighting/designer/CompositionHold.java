package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hold the timeline of a composition played through: it stopped at a hold point at the passed position and held
 * there for the passed time, until GO (see CompositionMarker). The current hold counts with the time held so far.
 * <p>
 * This mirrors composition-hold of the Rocket Show Designer.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompositionHold {

    private double positionMillis;
    private double heldMillis;

}
