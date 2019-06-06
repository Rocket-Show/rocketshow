package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * The wheels inside a fixture template. Used for @JsonAnySetter to work only on this part.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureTemplateWheels {

    private Map<String, FixtureWheel> wheels = new HashMap<>();

    @JsonAnyGetter
    public Map<String, FixtureWheel> getWheels() {
        return wheels;
    }

    @JsonAnySetter
    public void setWheels(Map<String, FixtureWheel> wheels) {
        this.wheels = wheels;
    }
}
