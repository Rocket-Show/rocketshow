package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EffectCurve.class, name = "curve"),
        @JsonSubTypes.Type(value = EffectPanTilt.class, name = "pan-tilt")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Effect {

    private final static Logger logger = LoggerFactory.getLogger(Effect.class);

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

    // the value the effect puts on its channels, or null if it does not apply at this
    // moment - the fixtures keep whatever the rest of the preset puts on them then.
    // the tempo is the one of the composition the effect is played in, which the curves
    // synced to the beat run on. it is null while a preset is previewed on its own.
    public abstract Double getValueAtMillis(long timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute);

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
