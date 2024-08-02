package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * A Rocket Show Designer fixture matrix.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FixtureMatrix {

    private List<List<String>> pixelKeys;
    private List<Integer> pixelCount;
    @JsonUnwrapped
    private FixtureProfileMatrixPixelGroups pixelGroups;
}
