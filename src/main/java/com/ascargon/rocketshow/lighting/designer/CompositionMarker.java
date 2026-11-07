package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A marker on the timeline of a composition of the designer: a label naming a part of it ("label"), or a hold point
 * ("hold"), where the composition stops until GO while the lights keep running (see CompositionPlayer.hold).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class CompositionMarker {

    public static final String TYPE_HOLD = "hold";

    private String uuid;
    private String name;
    private double positionMillis;
    private String type = "label";

    public boolean isHold() {
        return TYPE_HOLD.equals(type);
    }
}
