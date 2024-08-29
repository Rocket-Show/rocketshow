package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CachedFixture {

    private Fixture fixture;
    private String pixelKey;
    private FixtureProfile profile;
    private FixtureMode mode;
    private List<CachedFixtureChannel> channels = new ArrayList<>();
    private List<String> pixelKeysInOrder = new ArrayList<>();

}
