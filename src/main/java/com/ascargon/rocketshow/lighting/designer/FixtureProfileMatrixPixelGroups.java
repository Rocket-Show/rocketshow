package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rocket Show Designer fixture matrix pixelGroups.
 * Used, because the object name in the JSON is dynamic.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureProfileMatrixPixelGroups {

    // the list of string could be a single string (no JSON array), e.g. the keyword 'all'
    @JsonDeserialize(using = MapWithStringOrListDeserializer.class)
    private Map<String, List<String>> pixelGroups = new HashMap<>();

    public Map<String, List<String>> getPixelGroups() {
        return pixelGroups;
    }

    public void setPixelGroups(Map<String, List<String>> pixelGroups) {
        this.pixelGroups = pixelGroups;
    }
}
