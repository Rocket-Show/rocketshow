package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * The template channels inside a fixture profile.
 * Used for @JsonAnySetter to work only on this part, because the object name in the JSON is dynamic.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureProfileTemplateChannels {

    private Map<String, FixtureChannel> templateChannels = new HashMap<>();

    @JsonAnyGetter
    public Map<String, FixtureChannel> getTemplateChannels() {
        return templateChannels;
    }

    @JsonAnySetter
    public void setTemplateChannels(Map<String, FixtureChannel> templateChannels) {
        this.templateChannels = templateChannels;
    }
}
