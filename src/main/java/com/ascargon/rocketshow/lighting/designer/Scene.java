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

    // the folder of the scene tree of the designer the scene is in (null = top level) and its position among the
    // folders and scenes of that folder (see SceneFolder)
    private String folderUuid;
    private double sortIndex = 0;

    // how the designer marks the scene, which the Play screen shows it with as well: the font awesome class of its
    // icon (null = the default one) and its color. While colorAuto is set, the presets of the scene decide its color
    // and the picked one is only what it falls back to. The designer writes the color it shows (also the one decided
    // by the presets) as displayColor when it saves the project.
    private String icon;
    private String color;
    private boolean colorAuto = true;
    private String displayColor;

    // All contained presets, in the order they are layered in this scene: the first
    // one is the topmost layer, overwriting the values of the ones below it
    private String[] presetUuids;

    // how much of the scene reaches the stage: 1 = all of it, 0 = nothing. It scales
    // everything the scene's presets put out, the way the master dimmer scales the whole
    // project, and the fades take away from what it leaves. A project written before the
    // scenes had a dimmer brings none, which leaves it at full.
    private double dimmer = 1;

    // Fading times. A scene of the designer starts without fades, which a project not giving them keeps.
    private double fadeInMillis = 0;
    private double fadeOutMillis = 0;

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

    public String getFolderUuid() {
        return folderUuid;
    }

    public void setFolderUuid(String folderUuid) {
        this.folderUuid = folderUuid;
    }

    public double getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(double sortIndex) {
        this.sortIndex = sortIndex;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isColorAuto() {
        return colorAuto;
    }

    public void setColorAuto(boolean colorAuto) {
        this.colorAuto = colorAuto;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = displayColor;
    }

    public String[] getPresetUuids() {
        return presetUuids;
    }

    public void setPresetUuids(String[] presetUuids) {
        this.presetUuids = presetUuids;
    }

    public double getDimmer() {
        return dimmer;
    }

    public void setDimmer(double dimmer) {
        this.dimmer = dimmer;
    }

    public double getFadeInMillis() {
        return fadeInMillis;
    }

    public void setFadeInMillis(double fadeInMillis) {
        this.fadeInMillis = fadeInMillis;
    }

    public double getFadeOutMillis() {
        return fadeOutMillis;
    }

    public void setFadeOutMillis(double fadeOutMillis) {
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
