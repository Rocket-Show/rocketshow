package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rocket Show Designer fixture matrix pixelGroup constraints.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureMatrixPixelGroupConstraints {

    // constraint the pixel keys directly
    private List<String> keys;

    // constraints by coordinates
    private List<String> x;
    private List<String> y;
    private List<String> z;

    // a name regexp-constraint
    private List<String> name;

}
