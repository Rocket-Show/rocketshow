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

    // the project-file version (older ones are migrated when the project is loaded)
    private int version = 0;

    private String uuid;
    private String name;
    private float masterDimmerValue = 1;
    private String selectedPresetUuid;

    // the step of the selected preset the designer's panels are editing
    private String selectedStepUuid;

    // the designer is watching the steps of the selected preset run instead of holding
    // the one it is editing, and the point on its clock the run was started from
    private boolean stepPreviewRunning = false;
    private long stepPreviewStartMillis = 0;

    private List<String> selectedSceneUuids = new ArrayList<>();
    // play the selected preset on its own (solo) instead of the selected scenes
    private boolean previewPreset = false;
    private Composition[] compositions;
    private List<FixtureProfile> fixtureProfiles;

    // fixtures added to the project in a DMX universe
    private List<Fixture> fixtures;

    // the fixtures and pixel keys in order to be selectable
    public List<PresetFixture> presetFixtures;

    private List<Scene> scenes;
    private List<Preset> presets;

}
