package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CachedFixture {

    private Fixture fixture;
    private CachedFixturePixel pixel;
    private FixtureProfile profile;
    private FixtureMode mode;
    private List<CachedFixtureChannel> channels = new ArrayList<>();
    private List<String> pixelKeysInOrder = new ArrayList<>();

    // where the part is in its fixture, for the pixel effects (see DefaultDesignerService.getPixelPlace), worked out once
    private PixelContext pixelPlace;

}
