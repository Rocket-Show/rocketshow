package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.SceneLearning;
import com.ascargon.rocketshow.play.CompositionPlayer;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

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

    // the live controls of the scenes: the ones flashed, the tempo they run at and where it is from ("project", "tap",
    // "set" or "clock"), the speed master, the dimmer of each scene (the ones at full left out) and the scene waiting
    // for its key, pad or button of a MIDI controller (see ScenePlayerService)
    private List<String> flashedSceneUuids;
    private Double sceneBeatsPerMinute;
    private String sceneTempoSource;
    private double sceneSpeed = 1;
    private Map<String, Double> sceneDimmers;
    private SceneLearning sceneLearning;

}
