package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer fixture channel value.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureChannelValue {

    private String channelName;
    private String fixtureTemplateUuid;
    private Double value;

    public FixtureChannelValue(String channelName, String fixtureTemplateUuid, Double value) {
        this.channelName = channelName;
        this.fixtureTemplateUuid = fixtureTemplateUuid;
        this.value = value;
    }

    public FixtureChannelValue() {

    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getFixtureTemplateUuid() {
        return fixtureTemplateUuid;
    }

    public void setFixtureTemplateUuid(String fixtureTemplateUuid) {
        this.fixtureTemplateUuid = fixtureTemplateUuid;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
