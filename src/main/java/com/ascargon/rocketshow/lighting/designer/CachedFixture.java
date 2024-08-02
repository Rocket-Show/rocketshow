package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CachedFixture {

    private Fixture fixture;
    private FixtureProfile profile;
    private FixtureMode mode;
    private List<CachedFixtureChannel> channels = new ArrayList<>();

}
