package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A Rocket Show Designer fixture.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Fixture {

    private String uuid;
    private String profileUuid;
    private String name;
    private String dmxUniverseUuid = "";
    private int dmxFirstChannel;
    private String modeShortName;

    // where the fixture hangs: spread over the stage with the others (topFront, bottomFront, topBack, bottomBack) or
    // placed by hand (manual) at the position (in cm) and turned by the rotation (in degrees), like in the designer
    // (see StagePlacements)
    private String positioning = "topFront";
    private double positionX = 0;
    private double positionY = 0;
    private double positionZ = 0;
    private double rotationX = 0;
    private double rotationY = 0;
    private double rotationZ = 0;

}
