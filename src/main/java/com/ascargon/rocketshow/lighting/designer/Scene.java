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

    // All contained presets
    private String[] presetUuids;

    // Fading times
    private long fadeInMillis = 2000;
    private long fadeOutMillis = 2000;

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
}
