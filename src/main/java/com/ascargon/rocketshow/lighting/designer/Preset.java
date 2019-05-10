package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A Rocket Show Designer preset.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preset {

    private String uuid;
    private String name;

    // all related fixtures
    private String[] fixtures;

    // all properties. Also add the fine properties (16-bit values), if calculated.
    // The fixtures will pick up the corresponding values, if available.
    private FixtureCapabilityValue[] capabilityValues;

    // all related effects
    private Effect[] effects;

    // position offset, relative to the scene start
    // (undefined = start/end of the scene itself)
    private Long startMillis;
    private Long endMillis;

    // fading times
    private long fadeInMillis = 0;
    private long adeOutMillis = 0;

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

    public String[] getFixtures() {
        return fixtures;
    }

    public void setFixtures(String[] fixtures) {
        this.fixtures = fixtures;
    }

    public FixtureCapabilityValue[] getCapabilityValues() {
        return capabilityValues;
    }

    public void setCapabilityValues(FixtureCapabilityValue[] capabilityValues) {
        this.capabilityValues = capabilityValues;
    }

    public Effect[] getEffects() {
        return effects;
    }

    public void setEffects(Effect[] effects) {
        this.effects = effects;
    }

    public Long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(Long startMillis) {
        this.startMillis = startMillis;
    }

    public Long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(Long endMillis) {
        this.endMillis = endMillis;
    }

    public long getFadeInMillis() {
        return fadeInMillis;
    }

    public void setFadeInMillis(long fadeInMillis) {
        this.fadeInMillis = fadeInMillis;
    }

    public long getAdeOutMillis() {
        return adeOutMillis;
    }

    public void setAdeOutMillis(long adeOutMillis) {
        this.adeOutMillis = adeOutMillis;
    }

}
