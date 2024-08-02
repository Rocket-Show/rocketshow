package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * A Rocket Show Designer fixture mode.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureMode {

    private String name;
    private String shortName;

    // the list could contain a single name of a channel or an object in case of a matrix channel
    @JsonDeserialize(using = ListWithStringOrFixtureModeChannelDeserializer.class)
    private List<Object> channels;

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

    public List<Object> getChannels() {
        return channels;
    }

    public void setChannels(List<Object> channels) {
        this.channels = channels;
    }
}
