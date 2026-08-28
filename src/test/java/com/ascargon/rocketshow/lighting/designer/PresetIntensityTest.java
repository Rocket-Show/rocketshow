package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How much of what a preset puts out reaches the stage. These mirror the specs of
 * PreviewService in the Rocket Show Designer. A show has to look the same on stage as it
 * did while it was written, so both sides run the same cases.
 */
class PresetIntensityTest {

    // a scene held at the passed dimmer, without fades of its own: the cases which want
    // one ask for it
    private Scene scene(double dimmer) {
        Scene sceneWithDimmer = new Scene();
        sceneWithDimmer.setDimmer(dimmer);
        sceneWithDimmer.setFadeInMillis(0);
        sceneWithDimmer.setFadeOutMillis(0);

        return sceneWithDimmer;
    }

    private ScenePlaybackRegion region(long startMillis, long endMillis) {
        ScenePlaybackRegion playbackRegion = new ScenePlaybackRegion();
        playbackRegion.setStartMillis(startMillis);
        playbackRegion.setEndMillis(endMillis);

        return playbackRegion;
    }

    private double intensity(PresetRegionScene preset, long timeMillis) {
        return DefaultDesignerService.getPresetIntensity(preset, timeMillis);
    }

    @Test
    void letsASceneAtFullThrough() {
        PresetRegionScene playing = new PresetRegionScene(new Preset(), region(0, 10000), scene(1));

        assertEquals(1, intensity(playing, 5000));
    }

    @Test
    void scalesASceneOnTheTimelineByItsDimmer() {
        PresetRegionScene playing = new PresetRegionScene(new Preset(), region(0, 10000), scene(0.25));

        assertEquals(0.25, intensity(playing, 5000));
    }

    @Test
    void scalesASceneByItsDimmerWhileItIsOnlyBeingWatched() {
        // a selected scene is previewed without a region: the dimmer still holds it down
        PresetRegionScene previewed = new PresetRegionScene(new Preset(), null, scene(0.5));

        assertEquals(0.5, intensity(previewed, 5000));
    }

    @Test
    void leavesAPresetPlayedOnItsOwnAlone() {
        PresetRegionScene solo = new PresetRegionScene(new Preset(), null, null);

        assertEquals(1, intensity(solo, 5000));
    }

    @Test
    void takesTheSceneFadeOutOfWhatTheDimmerLeaves() {
        Scene dimmed = scene(0.5);
        dimmed.setFadeInMillis(1000);
        PresetRegionScene playing = new PresetRegionScene(new Preset(), region(0, 10000), dimmed);

        // half way into the fade, half of the half the dimmer leaves is through
        assertEquals(0.25, intensity(playing, 500), 0.0000000001);
        // and the dimmer alone holds it once the fade is over
        assertEquals(0.5, intensity(playing, 5000));
    }

    @Test
    void takesTheSceneFadeOutOverTheWholeFade() {
        Scene dimmed = scene(0.5);
        dimmed.setFadeOutMillis(1000);
        PresetRegionScene playing = new PresetRegionScene(new Preset(), region(0, 10000), dimmed);

        assertEquals(0.25, intensity(playing, 9500), 0.0000000001);
    }

    @Test
    void combinesTheSceneDimmerWithAPresetFade() {
        Preset preset = new Preset();
        preset.setFadeInMillis(1000);
        PresetRegionScene playing = new PresetRegionScene(preset, region(0, 10000), scene(0.5));

        assertEquals(0.25, intensity(playing, 500), 0.0000000001);
    }

    @Test
    void shutsASceneOffAtADimmerOfZero() {
        PresetRegionScene playing = new PresetRegionScene(new Preset(), region(0, 10000), scene(0));

        assertEquals(0, intensity(playing, 5000));
    }
}
