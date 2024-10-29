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
    private Integer slotNumber;
    private String brightness;
    private String brightnessStart;
    private String brightnessEnd;

}
