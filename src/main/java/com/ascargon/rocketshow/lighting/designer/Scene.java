package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A Rocket Show Designer scene.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scene {

    private String uuid;
    private String name;

    // All contained presets, in the order they are layered in this scene: the first
    // one is the topmost layer, overwriting the values of the ones below it
    private String[] presetUuids;

    // Fading times
    private long fadeInMillis = 2000;
    private long fadeOutMillis = 2000;

    // fade in/out outside the start/end times?
    private boolean fadeInPre = false;
    private boolean fadeOutPost = false;

    // how the fades are shaped over their time (see TransitionCurve)
    private String fadeInCurve = "linear";
    private String fadeOutCurve = "linear";

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

    public String[] getPresetUuids() {
        return presetUuids;
    }

    public void setPresetUuids(String[] presetUuids) {
        this.presetUuids = presetUuids;
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

    public String getFadeInCurve() {
        return fadeInCurve;
    }

    public void setFadeInCurve(String fadeInCurve) {
        this.fadeInCurve = fadeInCurve;
    }

    public String getFadeOutCurve() {
        return fadeOutCurve;
    }

    public void setFadeOutCurve(String fadeOutCurve) {
        this.fadeOutCurve = fadeOutCurve;
    }
}
