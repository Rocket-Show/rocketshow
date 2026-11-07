package com.ascargon.rocketshow.session;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The dimmer of a scene on the Play screen, from 0 to 1, on top of the dimmer the scene has in the designer.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
@NoArgsConstructor
public class SceneDimmer {

    private String sceneUuid;
    private double dimmer = 1;

    public SceneDimmer(String sceneUuid, double dimmer) {
        this.sceneUuid = sceneUuid;
        this.dimmer = dimmer;
    }

}
