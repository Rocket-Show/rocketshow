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
    // 1) and the speed master (1 = as written)
    void setLiveSceneDimmers(Map<String, Double> dimmers);

    void setLiveSpeed(double speed);

    // ---- the tempo (see TempoService) ----

    // The beats of the tempo playing, which the scenes picked by hand and the compositions following the tempo from
    // outside run on
    BeatClock getBeatClock();

    // The tempo from outside: tapped, set or of a MIDI clock (null = the programmed one). The scenes picked by hand and
    // the compositions without audio (and the default composition) follow it.
    void setOutsideBeatsPerMinute(Double beatsPerMinute);

    Double getOutsideBeatsPerMinute();

    // The tempo of the composition or the scenes playing (null while nothing plays)
    Double getProgrammedBeatsPerMinute();

    // The tempo playing: the one from outside, else the programmed one (null while neither is)
    Double getTempoBeatsPerMinute();

    // The beats of a bar of the composition or the scenes playing (4, if nothing plays)
    int getBeatsPerBar();

    // ---- the holds of the timeline (see CompositionPlayer) ----

    // The timeline of the composition playing holds at the passed hold point until GO (endHold): the position stands
    // still while the clips playing run on
    void hold(double positionMillis);

    void endHold();

    boolean isHolding();

    // The position jumped to the passed one: the holds from there on weren't gone through anymore
    void forgetHolds(double fromPositionMillis);

    // The holds the timeline of the designer went through, and whether it holds at the last of them (its live preview
    // holds along with it, at the position it is previewed from)
    void setPreviewHolds(List<CompositionHold> holds, boolean holding);

    // The positions of the hold points of the designer composition of the passed composition, in their order (none
    // without a designer project)
    List<Double> getHoldPositions(String compositionName);

    // Whether scenes were picked by hand, whether they play at the moment or wait for a composition to close
    boolean hasManualScenes();

    // Whether the scenes picked by hand play at the moment, instead of a composition or the preview of the designer
    boolean isPlayingManualScenes();

    Project getCurrentProject();

}
