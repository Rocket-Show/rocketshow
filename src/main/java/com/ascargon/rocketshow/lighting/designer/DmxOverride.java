package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * A DMX channel held at a value by a fader of the DMX channels of the designer, whatever the project puts on it: the
 * value is sent as it is, after the master dimmer, until the fader is let go.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DmxOverride {

    // the universe of the project the channel is in
    private String universeUuid;

    // the channel, counted from 0 like the first channel of a fixture (DMX address 1 is channel 0)
    private int dmxChannel;

    // the value sent, from 0 to 255
    private int value;

}
