package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer preset.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Preset {

    private String uuid;
    private String name;

    // all related fixtures, in the order they are chased in (only relevant, if
    // useGlobalFixtureOrder is false)
    private List<PresetFixture> fixtures = new ArrayList<>();

    // chase the fixtures in the global order (project.presetFixtures) instead of
    // this preset's own order
    private boolean useGlobalFixtureOrder = true;

    // the selected values
    // OBSOLETE: replaced with the values of the first step. The designer still writes
    // them when a project is saved, so that an older Rocket Show shows the first step
    // instead of nothing.
    private List<FixtureChannelValue> fixtureChannelValues;
    private List<FixtureCapabilityValue> fixtureCapabilityValues;

    // the states this preset runs through over its playing time, in the order they are
    // reached. Empty only in a project written before the designer knew steps, which
    // PresetSteps falls back to the values above for.
    private List<PresetStep> steps = new ArrayList<>();

    // start the sequence over instead of holding the last step
    private boolean stepsLoop = false;

    // the length of one pass (null = the last step holds as long as the one before it
    // lasted, see PresetSteps.getStepsLoopMillis)
    private Long stepsLoopMillis;

    // how the sequence is shifted from one fixture to the next (chasing), following the
    // same rules as the effect curves: "millis" shifts it by a fixed time, "spread"
    // distributes stepsPhasingCycles full passes over all fixtures of the preset.
    // both values are signed: a negative one chases in the opposite direction.
    private String stepsPhasingMode = "millis";
    private long stepsPhasingMillis = 0;
    private float stepsPhasingCycles = 1;

    // how many fixtures share the same chase step (1 = each fixture on its own)
    private int stepsPhasingGroupSize = 1;

    // all related effects. They stay on the preset instead of moving into the steps, so
    // that they keep their phase across a step transition: a step only opens or closes
    // them (see PresetStep.effectAmounts).
    private List<Effect> effects;

    // position offset, relative to the scene start
    // (null = start/end of the scene itself)
    private Long startMillis;
    private Long endMillis;

    // fading times
    private long fadeInMillis = 0;
    private long fadeOutMillis = 0;

    // fade in/out outside the start/end times?
    private boolean fadeInPre = false;
    private boolean fadeOutPost = false;

    // how the fades are shaped over their time (see TransitionCurve)
    private String fadeInCurve = "linear";
    private String fadeOutCurve = "linear";

}
