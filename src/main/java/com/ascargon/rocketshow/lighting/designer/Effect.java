package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer effect.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Effect {

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

    public double getValueAtMillis(long timeMillis, int fixtureIndex) {
        return 0;
    }

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
