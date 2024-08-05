package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A Rocket Show Designer fixture channel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureChannel {

    private List<String> fineChannelAliases;
    private String defaultValue;
    private FixtureCapability capability;
    private List<FixtureCapability> capabilities;
    private String dmxValueResolution;
}
