package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A Rocket Show Designer fixture mode channel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureModeChannel {

    // direct reference to an available channel
    private String name;

    // a matrix channel
    private String insert;
    // the list of string could be a single string (no JSON array), e.g. 'eachPixelABC'
    @JsonDeserialize(using = StringOrListDeserializer.class)
    private List<String> repeatFor;
    private String channelOrder;
    private List<String> templateChannels;

}
