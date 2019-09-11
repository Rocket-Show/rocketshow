package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer preview configuration.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preview {

    private boolean presetPreview;
    private List<String> sceneUuids = new ArrayList<>();
    private String presetUuid;

    public boolean isPresetPreview() {
        return presetPreview;
    }

    public void setPresetPreview(boolean presetPreview) {
        this.presetPreview = presetPreview;
    }

    public List<String> getSceneUuids() {
        return sceneUuids;
    }

    public void setSceneUuids(List<String> sceneUuids) {
        this.sceneUuids = sceneUuids;
    }

    public String getPresetUuid() {
        return presetUuid;
    }

    public void setPresetUuid(String presetUuid) {
        this.presetUuid = presetUuid;
    }
}
