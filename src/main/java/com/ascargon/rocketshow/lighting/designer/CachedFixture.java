package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

public class CachedFixture {

    private Fixture fixture;
    private FixtureProfile profile;
    private FixtureMode mode;
    private List<CachedFixtureChannel> channels = new ArrayList<>();

    public Fixture getFixture() {
        return fixture;
    }

    public void setFixture(Fixture fixture) {
        this.fixture = fixture;
    }

    public FixtureProfile getProfile() {
        return profile;
    }

    public void setProfile(FixtureProfile profile) {
        this.profile = profile;
    }

    public FixtureMode getMode() {
        return mode;
    }

    public void setMode(FixtureMode mode) {
        this.mode = mode;
    }

    public List<CachedFixtureChannel> getChannels() {
        return channels;
    }

    public void setChannels(List<CachedFixtureChannel> channels) {
        this.channels = channels;
    }
}
