package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Rocket Show Designer project.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ScenePlaybackRegion {

	private String sceneUuid;
    private long startMillis;
    private long endMillis;

    public String getSceneUuid() {
        return sceneUuid;
    }

    public void setSceneUuid(String sceneUuid) {
        this.sceneUuid = sceneUuid;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

}
