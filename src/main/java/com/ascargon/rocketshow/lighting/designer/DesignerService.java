package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.play.CompositionPlayer;
import org.freedesktop.gstreamer.Pipeline;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

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

    void setSelectedSceneUuids(List<String> selectedSceneUuids);

    // Play the scenes of the project picked by hand on the Play screen, dimmed by its master dimmer (0 to 1). They play
    // whenever no composition plays a project of its own: right away, or once the composition closes its project.
    void playManualScenes(Project project, List<String> sceneUuids, double masterDimmer);

    // Stop the scenes picked by hand and turn their lights off
    void stopManualScenes();

    // Whether scenes were picked by hand, whether they play at the moment or wait for a composition to close
    boolean hasManualScenes();

    // Whether the scenes picked by hand play at the moment, instead of a composition or the preview of the designer
    boolean isPlayingManualScenes();

    Project getCurrentProject();

}
