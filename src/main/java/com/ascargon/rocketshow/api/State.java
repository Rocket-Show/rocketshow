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

    // the live controls of the scenes: the ones flashed, the speed master, the dimmer of each scene (the ones at full
    // left out) and the scene waiting for its key, pad or button of a MIDI controller (see ScenePlayerService)
    private List<String> flashedSceneUuids;
    private double sceneSpeed = 1;
    private Map<String, Double> sceneDimmers;
    private SceneLearning sceneLearning;

    // the tempo playing (null while nothing plays without a tempo from outside), where it is from ("project", "tap",
    // "set" or "clock"), where its beats are at the moment (counted from when they started) and the beats of a bar
    // (see TempoService)
    private Double tempoBeatsPerMinute;
    private String tempoSource = "project";
    private double tempoBeats;
    private int tempoBeatsPerBar = 4;

    // whether the composition holds at a hold point until GO
    private boolean holding;

}
