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

    public enum EffectChannel
    {
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

    public abstract double getValueAtMillis(long timeMillis, Integer fixtureIndex);

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

}
