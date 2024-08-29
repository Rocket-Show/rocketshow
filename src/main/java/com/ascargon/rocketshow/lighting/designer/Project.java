package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer project.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Project {

    private String uuid;
    private String name;
    private float masterDimmerValue = 1;
    private String selectedPresetUuid;
    private List<String> selectedSceneUuids = new ArrayList<>();
    private boolean previewPreset = true;
    private Composition[] compositions;
    private List<FixtureProfile> fixtureProfiles;

    // fixtures added to the project in a DMX universe
    private List<Fixture> fixtures;

    // the fixtures and pixel keys in order to be selectable
    public List<PresetFixture> presetFixtures;

    private List<Scene> scenes;
    private List<Preset> presets;

}
