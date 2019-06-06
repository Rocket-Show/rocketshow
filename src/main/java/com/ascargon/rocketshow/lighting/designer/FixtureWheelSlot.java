package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A Rocket Show Designer fixture wheel slot.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureWheelSlot {

    public enum FixtureWheelSlotType {
        Open,
        Closed,
        Color,
        Gobo,
        Prism,
        Iris,
        Frost,
        AnimationGoboStart,
        AnimationGoboEnd,
    }

    private FixtureWheelSlotType type;
    private String name;
    private List<String> colors;

    public FixtureWheelSlotType getType() {
        return type;
    }

    public void setType(FixtureWheelSlotType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }
}
