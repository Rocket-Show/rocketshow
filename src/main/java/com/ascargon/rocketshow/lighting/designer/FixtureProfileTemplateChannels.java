package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * The template channels inside a fixture profile.
 * Used, because the object name in the JSON is dynamic.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureProfileTemplateChannels {

    private Map<String, FixtureChannel> templateChannels = new HashMap<>();

    public Map<String, FixtureChannel> getTemplateChannels() {
        return templateChannels;
    }

    public void setTemplateChannels(Map<String, FixtureChannel> templateChannels) {
        this.templateChannels = templateChannels;
    }
}
