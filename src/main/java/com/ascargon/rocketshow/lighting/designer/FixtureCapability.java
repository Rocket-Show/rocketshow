package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A Rocket Show Designer fixture capability.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        Generic
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
    private int[] dmxRange;

    public FixtureCapabilityType getType() {
        return type;
    }

    public void setType(FixtureCapabilityType type) {
        this.type = type;
    }

    public FixtureCapabilityColor getColor() {
        return color;
    }

    public void setColor(FixtureCapabilityColor color) {
        this.color = color;
    }

    public int[] getDmxRange() {
        return dmxRange;
    }

    public void setDmxRange(int[] dmxRange) {
        this.dmxRange = dmxRange;
    }
}
