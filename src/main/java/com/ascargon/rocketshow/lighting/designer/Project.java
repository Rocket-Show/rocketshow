package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
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
    private String selectedPresetUuid;
    private List<String> selectedSceneUuids = new ArrayList<>();
    private boolean previewPreset = true;
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

    public String getSelectedPresetUuid() {
        return selectedPresetUuid;
    }

    public void setSelectedPresetUuid(String selectedPresetUuid) {
        this.selectedPresetUuid = selectedPresetUuid;
    }

    public List<String> getSelectedSceneUuids() {
        return selectedSceneUuids;
    }

    public void setSelectedSceneUuids(List<String> selectedSceneUuids) {
        this.selectedSceneUuids = selectedSceneUuids;
    }

    public boolean isPreviewPreset() {
        return previewPreset;
    }

    public void setPreviewPreset(boolean previewPreset) {
        this.previewPreset = previewPreset;
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
