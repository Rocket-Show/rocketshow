package com.ascargon.rocketshow.lighting.designer;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Plays the scenes of a designer project picked by hand on the Play screen, e.g. to light a club while no composition
 * plays. A composition with a designer project of its own takes the lights over while it plays, the scenes come back
 * once it stops. What was picked is remembered with the session, so the scenes light the stage again after a restart.
 */
@Service
public interface ScenePlayerService {

    // The designer projects with scenes, to pick the scenes from
    List<SceneProject> getProjects();

    // The project of the picked scenes (null = none picked so far)
    String getProjectName();

    List<String> getActiveSceneUuids();

    // A blackout holds the picked scenes dark, until it is lifted again
    boolean isBlackout();

    // The master dimmer of the Play screen, from 0 to 1. It dims the scenes on top of the master dimmer of the project.
    double getMasterDimmer();

    // Whether scenes are picked, but a composition (or the preview of the designer) lights the stage at the moment
    boolean isHeld();

    // Play the passed scenes of the passed project instead of the ones picked so far (none = release them all)
    void setActiveScenes(String projectName, List<String> sceneUuids);

    // The time a scene is played until it is stopped: by other scenes or by turning all scenes off
    long UNTIL_STOPPED = 0;

    // Play one scene of the project, by its uuid or its name, instead of the scenes on so far. After the passed time
    // (or UNTIL_STOPPED) it is turned off again, unless other scenes were picked meanwhile. Throws an
    // IllegalArgumentException for a scene the project doesn't have.
    void playScene(String projectName, String scene, long durationMillis);

    // Turn all scenes off
    void releaseAll();

    void setBlackout(boolean blackout);

    void toggleBlackout();

    void setMasterDimmer(double masterDimmer);

    // ---- playing the scenes live ----

    // Play one scene of the project (by its uuid or its name) instead of the scenes on so far, or turn it off, if it is
    // on: like a tap on the Play screen
    void toggleScene(String projectName, String scene);

    // Light one scene of the project on top of the others, at once, while it is held (on), and turn it off again at
    // once (not on)
    void flashScene(String projectName, String scene, boolean on);

    List<String> getFlashedSceneUuids();

    // Play the next (1) or the previous (-1) scene of a folder of the project (by its uuid or its name, null = all its
    // scenes) instead of the scenes on so far, in the order the designer shows them: the one after the scene on last,
    // the first one, if none of them is on. The first and the last scene stay on at the ends. A project not passed is
    // the one of the scenes on.
    void stepScene(String projectName, String folder, int direction);

    // A beat of the tempo the scenes run at: the beats tapped (two at least) give the tempo, each falls on a beat
    void tapTempo();

    // The tempo the scenes run at, set by hand (null = the one of the project)
    void setBeatsPerMinute(Double beatsPerMinute);

    // The tempo of a MIDI clock, of each beat it ticks (on the beat after it was started)
    void setClockTempo(double beatsPerMinute, boolean onTheBeat);

    // The tempo the scenes run at, null while none play without a tempo of their own, and where it is from: "project",
    // "tap", "set" or "clock"
    Double getBeatsPerMinute();

    String getTempoSource();

    // The speed master: how fast the effects and steps of the scenes run, 1 = as written (from 0.1 to 4)
    void setSpeed(double speed);

    double getSpeed();

    // The dimmer of a scene on the Play screen, from 0 to 1, on top of the one it has in the designer
    void setSceneDimmer(String sceneUuid, double dimmer);

    Map<String, Double> getSceneDimmers();

    // ---- MIDI learn ----

    // Wait for the key, pad or button of a MIDI controller which plays the scene from now on: toggling it ("toggle")
    // or flashing it while it is held ("flash"). The next note or control change of the MIDI input is taken for it.
    void learn(String projectName, String scene, String mode);

    void cancelLearn();

    // The scene waiting for its key, pad or button (null = none)
    SceneLearning getLearning();

    // Take the passed MIDI message for the scene waiting for it, if one does. Returns whether it was taken.
    boolean learnMidi(javax.sound.midi.ShortMessage shortMessage);

    // The keys, pads and buttons learned for the scenes of the project
    List<SceneMidiMapping> getMidiMappings(String projectName);

    // Forget the key, pad or button learned for the scene, for the passed way of playing it
    void forgetMidi(String projectName, String scene, String mode);

}
