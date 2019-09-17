package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.composition.CompositionPlayer;
import org.freedesktop.gstreamer.Pipeline;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public interface DesignerService {

    Project getProjectByCompositionName(String compositionName);

    Project getProjectByName(String name);

    List<Project> getAllProjects();

    void saveProject(String project) throws IOException;

    void load(CompositionPlayer compositionPlayer, Project project, Pipeline pipeline);

    void play();

    void pause();

    void seek(long positionMillis);

    void close();

    long getPositionMillis();

    void startPreview(long positionMillis);

    void stopPreview();

    void setPreviewPreset(boolean previewPreset);

    void setPreviewComposition(String compositionName);

    void setSelectedPresetUuid(String selectedPresetUuid);

    void setSelectedSceneUuids(List<String> selectedSceneUuids);

    Project getCurrentProject();

    void updateProfiles() throws IOException;

}
