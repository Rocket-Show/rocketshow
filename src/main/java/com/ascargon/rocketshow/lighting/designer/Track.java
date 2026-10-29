package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A track on the timeline of a Rocket Show Designer composition. The scenes on a track are played one after the
 * other, its clips (the scene playback regions carrying its uuid) never overlap. Where the clips of different tracks
 * overlap, the track higher up in the list of the composition is on top.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class Track {

    private String uuid;
    private String name;

    // Help while working on the show in the designer: a hidden track is left out of the preview, and as soon as a
    // track is soloed, only the soloed ones are previewed. The show itself always plays all of its tracks.
    private boolean hidden = false;
    private boolean solo = false;

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

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isSolo() {
        return solo;
    }

    public void setSolo(boolean solo) {
        this.solo = solo;
    }

}
