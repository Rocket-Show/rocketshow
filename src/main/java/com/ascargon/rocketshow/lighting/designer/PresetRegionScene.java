package com.ascargon.rocketshow.lighting.designer;

public class PresetRegionScene {

    private Preset preset;
    private ScenePlaybackRegion region;
    private Scene scene;

    public PresetRegionScene(Preset preset, ScenePlaybackRegion region, Scene scene) {
        this.preset = preset;
        this.region = region;
        this.scene = scene;
    }

    public Preset getPreset() {
        return preset;
    }

    public void setPreset(Preset preset) {
        this.preset = preset;
    }

    public ScenePlaybackRegion getRegion() {
        return region;
    }

    public void setRegion(ScenePlaybackRegion region) {
        this.region = region;
    }

    public Scene getScene() {
        return scene;
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }
}
