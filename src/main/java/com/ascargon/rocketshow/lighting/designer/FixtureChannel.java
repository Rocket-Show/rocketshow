package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer fixture channel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureChannel {

    private String[] fineChannelAliases;
    private String defaultValue;
    private FixtureCapability capability;
    private FixtureCapability[] capabilities;
    private String dmxValueResolution;

    public String[] getFineChannelAliases() {
        return fineChannelAliases;
    }

    public void setFineChannelAliases(String[] fineChannelAliases) {
        this.fineChannelAliases = fineChannelAliases;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public FixtureCapability getCapability() {
        return capability;
    }

    public void setCapability(FixtureCapability capability) {
        this.capability = capability;
    }

    public FixtureCapability[] getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(FixtureCapability[] capabilities) {
        this.capabilities = capabilities;
    }

    public String getDmxValueResolution() {
        return dmxValueResolution;
    }

    public void setDmxValueResolution(String dmxValueResolution) {
        this.dmxValueResolution = dmxValueResolution;
    }
}
