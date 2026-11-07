package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

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

    // The time the timeline held between the passed positions, the ends included: the effects and the steps of a clip
    // spanning a hold keep running while the position stands still, so this is added to the time they have run for.
    public static double getHeldMillis(List<CompositionHold> holds, double fromMillis, double toMillis) {
        double heldMillis = 0;

        if (holds == null) {
            return heldMillis;
        }

        for (CompositionHold hold : holds) {
            if (hold.getPositionMillis() >= fromMillis && hold.getPositionMillis() <= toMillis) {
                heldMillis += hold.getHeldMillis();
            }
        }

        return heldMillis;
    }
}
