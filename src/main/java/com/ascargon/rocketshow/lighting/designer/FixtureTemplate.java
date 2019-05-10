package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A fixture template in Rocket Show format, similar to Open Fixture Library.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureTemplate {

    public enum FixtureType {
        Blinder,
        @JsonProperty("Color Changer")
        ColorChanger,
        Dimmer,
        Effect,
        Fan,
        Flower,
        Hazer,
        Laser,
        Matrix,
        @JsonProperty("Moving Head")
        MovingHead,
        @JsonProperty("Pixel Bar")
        PixelBar,
        Scanner,
        Smoke,
        Stand,
        Strobe,
        Other
    }

    private String name;
    private String uuid;
    private List<String> categories;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

}
