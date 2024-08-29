package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A Rocket Show Designer fixture matrix.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureMatrix {

    private List<List<List<String>>> pixelKeys;
    private List<Integer> pixelCount;

    // the list contains objects with a name and constraints
    @JsonDeserialize(using = FixtureMatrixPixelGroupListDeserializer.class)
    private List<FixtureMatrixPixelGroup> pixelGroups;
}
