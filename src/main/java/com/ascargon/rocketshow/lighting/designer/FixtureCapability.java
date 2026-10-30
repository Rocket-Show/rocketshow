package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A Rocket Show Designer fixture capability.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureCapability {

    public enum FixtureCapabilityType {
        NoFunction,
        ShutterStrobe,
        StrobeSpeed,
        StrobeDuration,
        Intensity,
        ColorIntensity,
        ColorPreset,
        ColorTemperature,
        Pan,
        PanContinuous,
        Tilt,
        TiltContinuous,
        PanTiltSpeed,
        WheelSlot,
        WheelShake,
        WheelSlotRotation,
        WheelRotation,
        Effec,
        EffectSpeed,
        EffectDuration,
        EffectParameter,
        SoundSensitivity,
        Focus,
        Zoom,
        Iris,
        IrisEffect,
        Frost,
        FrostEffect,
        Prism,
        PrismRotation,
        BladeInsertion,
        BladeRotation,
        BladeSystemRotation,
        Fog,
        FogOutput,
        FogType,
        BeamAngle,
        Rotation,
        Speed,
        Time,
        Maintenance,
        Generic,
        Effect,
        BeamPosition,
    }

    public enum FixtureCapabilityColor {
        Red,
        Green,
        Blue,
        Cyan,
        Magenta,
        Yellow,
        Amber,
        White,
        @JsonProperty("Warm White")
        WarmWhite,
        @JsonProperty("Cold White")
        ColdWhite,
        UV,
        Lime,
        Indigo
    }

    private FixtureCapabilityType type;
    private FixtureCapabilityColor color;
    private List<Integer> dmxRange;
    private Object wheel;
    // e.g. 2.5 for the split between the slots 2 and 3
    private Double slotNumber;
    private String brightness;
    private String brightnessStart;
    private String brightnessEnd;

    // what a shutter does (e.g. "Open" or "Strobe"), and whether it flashes at random or to the sound
    private String shutterEffect;
    private Boolean randomTiming;
    private Boolean soundControlled;

    // the ends of the ranges the positioned capabilities are played in (see CapabilityPositions), like the fixture
    // library writes them (e.g. "10deg", "wide" or "3200K")
    private String speed;
    private String speedStart;
    private String speedEnd;
    private String angleStart;
    private String angleEnd;
    private String distanceStart;
    private String distanceEnd;
    private String openPercentStart;
    private String openPercentEnd;
    private String frostIntensityStart;
    private String frostIntensityEnd;
    private String colorTemperatureStart;
    private String colorTemperatureEnd;
    private String duration;
    private String durationStart;
    private String durationEnd;
    private String fogOutput;
    private String fogOutputStart;
    private String fogOutputEnd;

    // the colors a color preset (e.g. a color macro) lights in
    private List<String> colors;

}
