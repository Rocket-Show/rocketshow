package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A fixture template in Rocket Show format, similar to Open Fixture Library.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
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

}
