package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.play.CompositionPlayer;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@XmlRootElement
@Getter
@Setter
public class State {

    private Integer currentCompositionIndex;
    private CompositionPlayer.PlayState playState;
    private String currentCompositionName;
    private Long currentCompositionDurationMillis;
    private Long positionMillis;
    private String currentSetName;

    // True while this device is following an incoming MIDI timecode, and the absolute position of
    // that timecode master
    private boolean midiTimecodeLocked;
    private Long midiTimecodeMillis;

    private String error;

    // the scenes picked by hand on the Play screen (see ScenePlayerService) and whether a composition holds them,
    // because it lights the stage with its own designer project at the moment
    private String sceneProjectName;
    private List<String> activeSceneUuids;
    private boolean sceneBlackout;
    private double sceneMasterDimmer = 1;
    private boolean scenesHeld;

}
