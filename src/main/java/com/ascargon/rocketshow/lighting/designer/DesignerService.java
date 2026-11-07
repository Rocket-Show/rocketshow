package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.play.CompositionPlayer;
import org.freedesktop.gstreamer.Pipeline;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public interface DesignerService {

    Project getProjectByCompositionName(String compositionName);

    Project getProjectByName(String name);

    long getCompositionEndMillis(String compositionName);

    void deleteProjectByName(String name);

    List<Project> getAllProjects();

    void saveProject(String project) throws IOException;


    void renameComposition(String previousName, String name) throws IOException;

    void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline);

    void play();

    void pause();

    void close();

    void startPreview(long positionMillis);

    void stopPreview();

    void setPreviewPreset(boolean previewPreset);

    void setPreviewComposition(String compositionName);

    void setSelectedPresetUuid(String selectedPresetUuid);

    void setSelectedStepUuid(String selectedStepUuid);

    void setStepPreviewRunning(boolean stepPreviewRunning, double stepPreviewStartMillis);

    void setHolds(List<CompositionHold> holds);

    void setSelectedSceneUuids(List<String> selectedSceneUuids);

    // The fixtures being identified from the fixture pool of the designer: they blink at full white, whatever the
    // presets do with them, until the next project is loaded
    void setIdentifyFixtureUuids(List<String> identifyFixtureUuids);

    // the fixtures the designer asks from its fixture pool to reset themselves or to switch their lamp on, over what the
    // presets do with them (see Maintenance)
    void setMaintenanceFixtureUuids(List<String> resetFixtureUuids, List<String> lampOnFixtureUuids);

    // The DMX channels held at a value by the faders of the DMX channels of the designer: sent as they are, whatever the
    // project puts on them, until the next project is loaded
    void setDmxOverrides(List<DmxOverride> dmxOverrides);

    // Play the scenes of the project picked by hand on the Play screen and the ones flashed on top of them, dimmed by
    // its master dimmer (0 to 1). They play whenever no composition plays a project of its own: right away, or once the
    // composition closes its project. A scene picked fades in and plays from the moment it is picked, one let go fades
    // out (see LiveScenes).
    void playManualScenes(Project project, List<String> sceneUuids, List<String> flashedSceneUuids, double masterDimmer);

    default void playManualScenes(Project project, List<String> sceneUuids, double masterDimmer) {
        playManualScenes(project, sceneUuids, List.of(), masterDimmer);
    }

    // Let go of all scenes picked by hand: they fade out, then their lights are turned off
    void releaseManualScenes();

    // Stop the scenes picked by hand and turn their lights off at once
    void stopManualScenes();

    // The live controls of the Play screen for the scenes picked by hand: the dimmer of each scene (by its uuid, 0 to
    // 1), the speed master (1 = as written) and the tempo played live (null = the one of the project), which a beat
    // falls on right now, if asked to (e.g. a beat tapped)
    void setLiveSceneDimmers(Map<String, Double> dimmers);

    void setLiveSpeed(double speed);

    void setLiveBeatsPerMinute(Double beatsPerMinute, boolean onTheBeat);

    // The tempo the beat-synced curves of the scenes picked by hand are written for: the one of their project (null
    // while they don't play)
    Double getLiveReferenceBeatsPerMinute();

    // Whether scenes were picked by hand, whether they play at the moment or wait for a composition to close
    boolean hasManualScenes();

    // Whether the scenes picked by hand play at the moment, instead of a composition or the preview of the designer
    boolean isPlayingManualScenes();

    Project getCurrentProject();

}
