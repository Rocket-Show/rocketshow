package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * A fixture template in Rocket Show format, similar to Open Fixture Library.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureProfile {

    public enum FixtureCategory {
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
        Other,
        @JsonProperty("Barrel Scanner")
        BarrelScanner

    }

    private String uuid;
    private String name;
    private List<FixtureCategory> categories;
    private FixturePhysical physical;
    private FixtureMatrix matrix;
    @JsonUnwrapped
    private FixtureProfileAvailableChannels availableChannels;
    @JsonUnwrapped
    private FixtureProfileTemplateChannels templateChannels;
    @JsonUnwrapped
    private FixtureProfileWheels wheels;
    private List<FixtureMode> modes;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<FixtureCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<FixtureCategory> categories) {
        this.categories = categories;
    }

    public FixturePhysical getPhysical() {
        return physical;
    }

    public void setPhysical(FixturePhysical physical) {
        this.physical = physical;
    }

    public FixtureMatrix getMatrix() {
        return matrix;
    }

    public void setMatrix(FixtureMatrix matrix) {
        this.matrix = matrix;
    }

    public FixtureProfileAvailableChannels getAvailableChannels() {
        return availableChannels;
    }

    public void setAvailableChannels(FixtureProfileAvailableChannels availableChannels) {
        this.availableChannels = availableChannels;
    }

    public FixtureProfileTemplateChannels getTemplateChannels() {
        return templateChannels;
    }

    public void setTemplateChannels(FixtureProfileTemplateChannels templateChannels) {
        this.templateChannels = templateChannels;
    }

    public FixtureProfileWheels getWheels() {
        return wheels;
    }

    public void setWheels(FixtureProfileWheels wheels) {
        this.wheels = wheels;
    }

    public List<FixtureMode> getModes() {
        return modes;
    }

    public void setModes(List<FixtureMode> modes) {
        this.modes = modes;
    }
}
