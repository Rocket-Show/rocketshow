package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A Rocket Show Designer project.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    private String uuid;
    private String name;
    private float masterDimmerValue = 1;
    private Composition[] compositions;
    private List<FixtureProfile> fixtureProfiles;
    private List<Fixture> fixtures;
    private List<Scene> scenes;
    private List<Preset> presets;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getMasterDimmerValue() {
        return masterDimmerValue;
    }

    public void setMasterDimmerValue(float masterDimmerValue) {
        this.masterDimmerValue = masterDimmerValue;
    }

    public Composition[] getCompositions() {
        return compositions;
    }

    public void setCompositions(Composition[] compositions) {
        this.compositions = compositions;
    }

    public List<FixtureProfile> getFixtureProfiles() {
        return fixtureProfiles;
    }

    public void setFixtureProfiles(List<FixtureProfile> fixtureProfiles) {
        this.fixtureProfiles = fixtureProfiles;
    }

    public List<Fixture> getFixtures() {
        return fixtures;
    }

    public void setFixtures(List<Fixture> fixtures) {
        this.fixtures = fixtures;
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = scenes;
    }

    public List<Preset> getPresets() {
        return presets;
    }

    public void setPresets(List<Preset> presets) {
        this.presets = presets;
    }
}
