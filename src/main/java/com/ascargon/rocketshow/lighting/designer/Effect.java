package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
// an effect of a type this Rocket Show does not know (written by a newer designer) is read as an EffectUnknown, which
// lights nothing: the rest of the show still plays (see ProjectMigration)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        visible = true,
        defaultImpl = EffectUnknown.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = EffectCurve.class, name = "curve"),
        @JsonSubTypes.Type(value = EffectPanTilt.class, name = "pan-tilt"),
        @JsonSubTypes.Type(value = EffectPixel.class, name = "pixel")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Effect {

    public enum EffectChannel {
        colorRed,
        colorGreen,
        colorBlue,
        // TODO
        // hue,
        // saturation,
        dimmer,
        pan,
        tilt
    }

    private String uuid;
    private EffectChannel[] effectChannels;
    private boolean visible = true;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public EffectChannel[] getEffectChannels() {
        return effectChannels;
    }

    public void setEffectChannels(EffectChannel[] effectChannels) {
        this.effectChannels = effectChannels;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
