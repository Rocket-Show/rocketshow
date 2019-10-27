package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A Rocket Show Designer fixture channel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureChannel {

    private List<String> fineChannelAliases;
    private String defaultValue;
    private FixtureCapability capability;
    private List<FixtureCapability> capabilities;
    private String dmxValueResolution;

    public List<String> getFineChannelAliases() {
        return fineChannelAliases;
    }

    public void setFineChannelAliases(List<String> fineChannelAliases) {
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

    public List<FixtureCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<FixtureCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public String getDmxValueResolution() {
        return dmxValueResolution;
    }

    public void setDmxValueResolution(String dmxValueResolution) {
        this.dmxValueResolution = dmxValueResolution;
    }
}
