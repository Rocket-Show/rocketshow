package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

/**
 * Light the stage with the scenes of a designer project, like the Play screen does by hand (see ScenePlayerService),
 * e.g. from a MIDI note or at a position of a composition.
 */
@Getter
@Setter
public class ActionScene extends Action {

    public enum SceneActionType {
        // play one scene instead of the scenes on so far, for a while or until the next one
        PLAY_SCENE,
        // play one scene instead of the scenes on so far, or turn it off, if it is on (like a tap on the Play screen)
        TOGGLE_SCENE,
        // light one scene on top of the others while the key or button triggering it is held (or for a while)
        FLASH_SCENE,
        // play the next or the previous scene of a folder instead of the scenes on so far (see ScenePlayerService.stepScene)
        NEXT_SCENE,
        PREVIOUS_SCENE,
        // a beat tapped: kept, so the actions saved with it still load (see ActionTempo, which taps now)
        TAP_TEMPO,
        // turn all scenes off
        RELEASE_ALL,
        BLACKOUT_ON,
        BLACKOUT_OFF,
        TOGGLE_BLACKOUT
    }

    private SceneActionType sceneActionType;

    // the scene to play: the designer project and the scene in it, by its uuid or by its name
    private String projectName;
    private String scene;

    // the folder of the scenes NEXT_SCENE and PREVIOUS_SCENE step through, by its uuid or its name (none = all scenes
    // of the project)
    private String folder;

    // play the scene until it is stopped (by another scene or by turning all scenes off), instead of for a while
    private boolean untilStopped = false;

    // how long the scene plays otherwise, before it is turned off again (0 = until stopped as well)
    private long durationMillis = 0;

    @Override
    public ActionType getType() {
        return ActionType.SCENE;
    }

}
