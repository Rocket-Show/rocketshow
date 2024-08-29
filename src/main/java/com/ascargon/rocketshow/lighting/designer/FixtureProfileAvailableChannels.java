package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The available channels inside a fixture profile.
 * Used for @JsonAnySetter to work only on this part, because the object name in the JSON is dynamic.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureProfileAvailableChannels {

    private Map<String, FixtureChannel> availableChannels = new HashMap<>();

    @JsonAnyGetter
    public Map<String, FixtureChannel> getAvailableChannels() {
        return availableChannels;
    }

    @JsonAnySetter
    public void setAvailableChannels(Map<String, FixtureChannel> availableChannels) {
        this.availableChannels = availableChannels;
    }
}
