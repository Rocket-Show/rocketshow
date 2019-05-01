package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A Rocket Show Designer composition.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Composition {

    private String name;
    private String uuid;
    private String syncType;
    private long durationMillis;
    private List<ScenePlaybackRegion> scenePlaybackRegions;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getSyncType() {
        return syncType;
    }

    public void setSyncType(String syncType) {
        this.syncType = syncType;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public List<ScenePlaybackRegion> getScenePlaybackRegions() {
        return scenePlaybackRegions;
    }

    public void setScenePlaybackRegions(List<ScenePlaybackRegion> scenePlaybackRegions) {
        this.scenePlaybackRegions = scenePlaybackRegions;
    }

}
