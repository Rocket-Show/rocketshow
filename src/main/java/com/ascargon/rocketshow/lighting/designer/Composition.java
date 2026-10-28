package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
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

    // the tempo of the composition, which the curves synced to the beat run on
    private double beatsPerMinute = EffectCurve.DEFAULT_BEATS_PER_MINUTE;

    // the lanes of the timeline, the topmost first. Where the clips of two tracks overlap, the one higher up is on top.
    private List<Track> tracks = new ArrayList<>();

    private ScenePlaybackRegion[] scenePlaybackRegions;

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

    public double getBeatsPerMinute() {
        return beatsPerMinute;
    }

    public void setBeatsPerMinute(double beatsPerMinute) {
        this.beatsPerMinute = beatsPerMinute;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks == null ? new ArrayList<>() : tracks;
    }

    public ScenePlaybackRegion[] getScenePlaybackRegions() {
        return scenePlaybackRegions;
    }

    public void setScenePlaybackRegions(ScenePlaybackRegion[] scenePlaybackRegions) {
        this.scenePlaybackRegions = scenePlaybackRegions;
    }
}
