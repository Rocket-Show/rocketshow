package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer fixture mode.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureMode {

    private String name;
    private String shortName;

    // the list could contain a single name of a channel or an object in case of a matrix channel
    @JsonDeserialize(using = FixtureModeChannelListDeserializer.class)
    private List<FixtureModeChannel> channels = new ArrayList<>();

}
