package com.ascargon.rocketshow.lighting.designer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A key, pad or button of a MIDI controller which plays a scene, learned on the Play screen: a note ("note") or a
 * control change ("cc") on a channel (counted from 0, null = any), toggling the scene ("toggle") or flashing it while it
 * is held ("flash").
 *
 * @author Moritz A. Vieli
 */
@Getter
@AllArgsConstructor
public class SceneMidiMapping {

    private final String scene;
    private final String mode;
    private final String kind;
    private final Integer channel;
    private final Integer number;

}
