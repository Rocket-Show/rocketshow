package com.ascargon.rocketshow.lighting.designer;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A designer project as the Play screen picks scenes from it: only its scenes and their folders, without the fixtures
 * and presets the Play screen doesn't need.
 */
@Getter
public class SceneProject {

    private final String name;
    private final List<Scene> scenes;
    private final List<SceneFolder> sceneFolders;

    public SceneProject(Project project) {
        this.name = project.getName();
        this.scenes = project.getScenes() == null ? new ArrayList<>() : project.getScenes();
        this.sceneFolders = project.getSceneFolders() == null ? new ArrayList<>() : project.getSceneFolders();
    }

}
