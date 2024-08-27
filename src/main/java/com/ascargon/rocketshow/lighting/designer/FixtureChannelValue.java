package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A Rocket Show Designer fixture channel value.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureChannelValue {

    private String channelName;
    private String profileUuid;
    private Double value;

}
