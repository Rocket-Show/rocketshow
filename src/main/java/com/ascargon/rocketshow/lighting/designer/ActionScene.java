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

    // how long the scene plays, before it is turned off again (0 = until other scenes are picked)
    private long durationMillis = 0;

    @Override
    public ActionType getType() {
        return ActionType.SCENE;
    }

}
