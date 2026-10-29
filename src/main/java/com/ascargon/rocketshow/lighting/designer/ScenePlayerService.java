package com.ascargon.rocketshow.lighting.designer;

import org.springframework.stereotype.Service;

import java.util.List;

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

    // Play one scene of the project, by its uuid or its name, instead of the scenes on so far. After the passed time
    // (0 = none) it is turned off again, unless other scenes were picked meanwhile. Throws an IllegalArgumentException
    // for a scene the project doesn't have.
    void playScene(String projectName, String scene, long durationMillis);

    // Turn all scenes off
    void releaseAll();

    void setBlackout(boolean blackout);

    void toggleBlackout();

    void setMasterDimmer(double masterDimmer);

}
