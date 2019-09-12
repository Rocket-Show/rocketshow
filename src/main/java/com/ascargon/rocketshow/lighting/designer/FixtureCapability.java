package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
        Generic,
        Effect,
        BeamPosition
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

    public List<Integer> getDmxRange() {
        return dmxRange;
    }

    public void setDmxRange(List<Integer> dmxRange) {
        this.dmxRange = dmxRange;
    }

    public Object getWheel() {
        return null;
    }

    public void setWheel(Object wheel) {
        this.wheel = wheel;
    }

    public Integer getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Integer slotNumber) {
        this.slotNumber = slotNumber;
    }

    public String getBrightness() {
        return brightness;
    }

    public void setBrightness(String brightness) {
        this.brightness = brightness;
    }

    public String getBrightnessStart() {
        return brightnessStart;
    }

    public void setBrightnessStart(String brightnessStart) {
        this.brightnessStart = brightnessStart;
    }

    public String getBrightnessEnd() {
        return brightnessEnd;
    }

    public void setBrightnessEnd(String brightnessEnd) {
        this.brightnessEnd = brightnessEnd;
    }
}
