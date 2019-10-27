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
    private String[] fixtureUuids;

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

    public String[] getFixtureUuids() {
        return fixtureUuids;
    }

    public void setFixtureUuids(String[] fixtureUuids) {
        this.fixtureUuids = fixtureUuids;
    }

    public List<FixtureChannelValue> getFixtureChannelValues() {
        return fixtureChannelValues;
    }

    public void setFixtureChannelValues(List<FixtureChannelValue> fixtureChannelValues) {
        this.fixtureChannelValues = fixtureChannelValues;
    }

    public List<FixtureCapabilityValue> getFixtureCapabilityValues() {
        return fixtureCapabilityValues;
    }

    public void setFixtureCapabilityValues(List<FixtureCapabilityValue> fixtureCapabilityValues) {
        this.fixtureCapabilityValues = fixtureCapabilityValues;
    }

    public List<Effect> getEffects() {
        return effects;
    }

    public void setEffects(List<Effect> effects) {
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

    public long getFadeOutMillis() {
        return fadeOutMillis;
    }

    public void setFadeOutMillis(long fadeOutMillis) {
        this.fadeOutMillis = fadeOutMillis;
    }

    public boolean isFadeInPre() {
        return fadeInPre;
    }

    public void setFadeInPre(boolean fadeInPre) {
        this.fadeInPre = fadeInPre;
    }

    public boolean isFadeOutPost() {
        return fadeOutPost;
    }

    public void setFadeOutPost(boolean fadeOutPost) {
        this.fadeOutPost = fadeOutPost;
    }
}
