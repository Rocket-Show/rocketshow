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

    private String name;
    private String uuid;
    private Composition[] compositions;
    private FixtureTemplate[] fixtureTemplates;
    private Fixture[] fixtures;
    private Scene[] scenes;
    private Preset[] presets;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Composition[] getCompositions() {
        return compositions;
    }

    public void setCompositions(Composition[] compositions) {
        this.compositions = compositions;
    }

    public FixtureTemplate[] getFixtureTemplates() {
        return fixtureTemplates;
    }

    public void setFixtureTemplates(FixtureTemplate[] fixtureTemplates) {
        this.fixtureTemplates = fixtureTemplates;
    }

    public Fixture[] getFixtures() {
        return fixtures;
    }

    public void setFixtures(Fixture[] fixtures) {
        this.fixtures = fixtures;
    }

    public Scene[] getScenes() {
        return scenes;
    }

    public void setScenes(Scene[] scenes) {
        this.scenes = scenes;
    }

    public Preset[] getPresets() {
        return presets;
    }

    public void setPresets(Preset[] presets) {
        this.presets = presets;
    }
}
