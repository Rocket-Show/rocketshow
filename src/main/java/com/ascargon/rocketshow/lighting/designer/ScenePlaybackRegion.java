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
    // the designer keeps the times it is given, also the parts of a millisecond (e.g. of a clip as long as a bar)
    private double startMillis;
    private double endMillis;

    // the track of the composition the scene is played on
    private String trackUuid;

    public String getSceneUuid() {
        return sceneUuid;
    }

    public void setSceneUuid(String sceneUuid) {
        this.sceneUuid = sceneUuid;
    }

    public double getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(double startMillis) {
        this.startMillis = startMillis;
    }

    public double getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(double endMillis) {
        this.endMillis = endMillis;
    }

    public String getTrackUuid() {
        return trackUuid;
    }

    public void setTrackUuid(String trackUuid) {
        this.trackUuid = trackUuid;
    }

}
