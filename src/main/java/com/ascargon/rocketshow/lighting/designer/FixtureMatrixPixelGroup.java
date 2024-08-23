package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Rocket Show Designer fixture matrix pixelGroup.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureMatrixPixelGroup {

    private String name;
    private boolean isAll = false;
    private FixtureMatrixPixelGroupConstraints constraints = new FixtureMatrixPixelGroupConstraints();

}
