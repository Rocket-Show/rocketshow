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
    private double masterDimmerValue = 1;
    private String selectedPresetUuid;

    // the step of the selected preset the designer's panels are editing
    private String selectedStepUuid;

    // the designer is watching the steps of the selected preset run instead of holding
    // the one it is editing, and the point on its clock the run was started from
    private boolean stepPreviewRunning = false;
    private double stepPreviewStartMillis = 0;

    private List<String> selectedSceneUuids = new ArrayList<>();

    // the fixtures the designer is identifying from its fixture pool: they blink at full white in its live preview
    private List<String> identifyFixtureUuids = new ArrayList<>();

    // the fixtures the designer asks from its fixture pool to reset themselves or to switch their lamp on (see
    // Maintenance)
    private List<String> resetFixtureUuids = new ArrayList<>();
    private List<String> lampOnFixtureUuids = new ArrayList<>();

    // the DMX channels the designer holds at a value with the faders of its DMX channels, over what the project puts on
    // them (see DmxOverride)
    private List<DmxOverride> dmxOverrides = new ArrayList<>();

    // the hold points the timeline of the designer held at, with the time held at each, and whether it stands at the
    // last of them, waiting for GO: its live preview holds along with it (see CompositionHold)
    private List<CompositionHold> previewHolds = new ArrayList<>();
    private boolean previewHolding = false;

    // the stage the fixtures are spread over (in cm), like in the designer
    private double stageWidthCm = 600;
    private double stageHeightCm = 350;
    private double stageDepthCm = 600;
    private double stageFloorHeightCm = 30;

    // the composition open in the designer, whose tempo the curves synced to the beat follow while it isn't played
    private String selectedCompositionUuid;
    // play the selected preset on its own (solo) instead of the selected scenes
    private boolean previewPreset = false;

    // Whether the dimmers of a fixture which lights in colors only (e.g. an RGB par or a pixel bar with a master
    // dimmer) start at full where its profile does not say where they start: a color is all it takes to light it, like
    // a fixture without a dimmer (see DefaultDesignerService.openColorDimmers). It stays dark while its colors are off.
    // A project of a version before 11 keeps its dimmers off, the way it was played then.
    private boolean colorDimmersAtFull = true;
    private Composition[] compositions;
    private List<FixtureProfile> fixtureProfiles;

    // fixtures added to the project in a DMX universe
    private List<Fixture> fixtures;

    // the fixtures and pixel keys in order to be selectable
    public List<PresetFixture> presetFixtures;

    private List<Scene> scenes;

    // the folders the scenes are grouped in
    private List<SceneFolder> sceneFolders;

    private List<Preset> presets;

}
