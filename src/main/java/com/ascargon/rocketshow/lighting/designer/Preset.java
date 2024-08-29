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

    // all related fixtures
    private List<PresetFixture> fixtures = new ArrayList<>();

    // the selected values
    private List<FixtureChannelValue> fixtureChannelValues;
    private List<FixtureCapabilityValue> fixtureCapabilityValues;

    // all related effects
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

}
