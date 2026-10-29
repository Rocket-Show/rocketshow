package com.ascargon.rocketshow.lighting.designer;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class DmxChannelAndPixelKey {

    private int dmxChannel;
    private String pixelKey;
    private String dmxUniverseUuid;

}