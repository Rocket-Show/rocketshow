package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rocket Show Designer fixture matrix pixelGroup.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureProfileMatrixPixelGroup {

    private String name;
    private boolean isAll = false;
    private FixtureMatrixPixelGroupConstraints constraints = new FixtureMatrixPixelGroupConstraints();

}
