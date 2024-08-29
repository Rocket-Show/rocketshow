package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer cached fixture channel.
 *
 * @author Moritz A. Vieli
 */
@Setter
@Getter
public class CachedFixtureChannel {

    // the corresponding channel
    private FixtureChannel channel;

    // the name of the channel
    private String name;

    // all channel capabilities
    private List<CachedFixtureCapability> capabilities = new ArrayList<>();

    // default value
    private double defaultValue;

    // maximum value
    private double maxValue;

    // a color wheel, if available
    private FixtureWheel colorWheel;

}
