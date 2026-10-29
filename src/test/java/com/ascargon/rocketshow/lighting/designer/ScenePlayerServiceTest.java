package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.composition.DefaultSetService;
import com.ascargon.rocketshow.session.DefaultSessionService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scenes picked by hand on the Play screen: what the engine is told to play and what a unit remembers over a
 * restart.
 */
class ScenePlayerServiceTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private DesignerService designerService;
    private Project club;

    private static Project project(String name, int sceneCount) {
        Project project = new Project();
        project.setName(name);
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < sceneCount; i++) {
            Scene scene = new Scene();
            scene.setUuid(name + "-" + i);
            scene.setName("Scene " + i);
            scenes.add(scene);
        }
        project.setScenes(scenes);
        return project;
    }

    @BeforeEach
    void setUp() {
        settingsService = TestSettings.inDirectory(directory);
        designerService = mock(DesignerService.class);
        club = project("Club Lights", 2);
        when(designerService.getProjectByName("Club Lights")).thenReturn(club);
        when(designerService.getAllProjects()).thenReturn(List.of(project("Wedding", 0), club, project("bar", 1)));
    }

    private DefaultScenePlayerService scenePlayerService() {
        return new DefaultScenePlayerService(designerService, new DefaultSessionService(settingsService, new DefaultSetService()));
    }

    @Test
    void listsTheProjectsWithScenesByName() {
        List<SceneProject> projects = scenePlayerService().getProjects();

        assertEquals(List.of("bar", "Club Lights"), projects.stream().map(SceneProject::getName).toList());
        assertEquals(2, projects.get(1).getScenes().size());
    }

    @Test
    void playsThePickedScenesDimmedByTheMasterDimmer() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.setMasterDimmer(0.8);
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-1"));
        verify(designerService).playManualScenes(club, List.of("Club Lights-1"), 0.8);

        // A blackout holds them dark
        scenePlayerService.setBlackout(true);
        verify(designerService).playManualScenes(club, List.of("Club Lights-1"), 0.0);

        // Nothing is left to play once they are released and the blackout is lifted
        scenePlayerService.setActiveScenes("Club Lights", List.of());
        scenePlayerService.setBlackout(false);
        verify(designerService, org.mockito.Mockito.atLeastOnce()).stopManualScenes();
    }

    @Test
    void keepsTheMasterDimmerBetweenOffAndFull() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.setMasterDimmer(1.5);
        assertEquals(1, scenePlayerService.getMasterDimmer());

        scenePlayerService.setMasterDimmer(-1);
        assertEquals(0, scenePlayerService.getMasterDimmer());
    }

    @Test
    void lightsTheStageAgainWithThePickedScenesAfterARestart() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();
        scenePlayerService.setMasterDimmer(0.5);
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-0", "Club Lights-1"));

        designerService = mock(DesignerService.class);
        when(designerService.getProjectByName("Club Lights")).thenReturn(club);
        DefaultScenePlayerService restarted = scenePlayerService();

        verify(designerService).playManualScenes(club, List.of("Club Lights-0", "Club Lights-1"), 0.5);
        assertEquals("Club Lights", restarted.getProjectName());
        assertEquals(List.of("Club Lights-0", "Club Lights-1"), restarted.getActiveSceneUuids());
        assertFalse(restarted.isBlackout());
    }

    @Test
    void stopsTheScenesOfAProjectWhichIsGone() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.setActiveScenes("Deleted", List.of("scene"));

        verify(designerService, never()).playManualScenes(any(), anyList(), anyDouble());
        verify(designerService, org.mockito.Mockito.atLeastOnce()).stopManualScenes();
    }

    @Test
    void isHeldWhileACompositionLightsTheStage() {
        when(designerService.hasManualScenes()).thenReturn(true);
        assertTrue(scenePlayerService().isHeld());

        when(designerService.isPlayingManualScenes()).thenReturn(true);
        assertFalse(scenePlayerService().isHeld());
    }

}
