package com.ascargon.rocketshow.lighting.designer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A scene waiting for the key, pad or button of a MIDI controller which is to play it from now on (see
 * ScenePlayerService.learn): toggle it on and off ("toggle"), or flash it while it is held ("flash").
 *
 * @author Moritz A. Vieli
 */
@Getter
@AllArgsConstructor
public class SceneLearning {

    private final String projectName;
    private final String scene;
    private final String mode;

}
