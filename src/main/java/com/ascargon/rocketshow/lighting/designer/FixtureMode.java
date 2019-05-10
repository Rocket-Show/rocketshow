package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer fixture mode.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureMode {

    private String name;
    private String shortName;
    private String[] channels;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String[] getChannels() {
        return channels;
    }

    public void setChannels(String[] channels) {
        this.channels = channels;
    }
}
