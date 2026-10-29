package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.DefaultSetService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.session.DefaultSessionService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
    private NotificationService notificationService;
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
        notificationService = mock(NotificationService.class);
        club = project("Club Lights", 2);
        when(designerService.getProjectByName("Club Lights")).thenReturn(club);
        when(designerService.getAllProjects()).thenReturn(List.of(project("Wedding", 0), club, project("bar", 1)));
    }

    private DefaultScenePlayerService scenePlayerService() {
        return new DefaultScenePlayerService(designerService, new DefaultSessionService(settingsService, new DefaultSetService()), notificationService, mock(PlayerService.class), new DefaultSetService());
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
    void dimsTheScenesPlayingWhenTheMasterDimmerMoves() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-0"));

        scenePlayerService.setMasterDimmer(0.3);

        verify(designerService).playManualScenes(club, List.of("Club Lights-0"), 0.3);
    }

    @Test
    void keepsTheStageDarkWithABlackoutWithoutScenes() {
        // also over the lights of the default composition
        DefaultScenePlayerService scenePlayerService = scenePlayerService();
        scenePlayerService.setActiveScenes("Club Lights", List.of());

        scenePlayerService.setBlackout(true);

        verify(designerService).playManualScenes(club, List.of(), 0.0);
    }

    @Test
    void listsTheScenesInTheirFolders() {
        SceneFolder folder = new SceneFolder();
        folder.setUuid("ballads");
        folder.setName("Ballads");
        club.setSceneFolders(List.of(folder));

        List<SceneProject> projects = scenePlayerService().getProjects();

        assertEquals(List.of("Ballads"), projects.get(1).getSceneFolders().stream().map(SceneFolder::getName).toList());
        assertEquals(List.of(), projects.get(0).getSceneFolders());
    }

    @Test
    void givesTheAppsTheScenesWithTheirPlacesAndLooks() {
        // The Play screen shows them in the folders and the order of the designer, with their icon and color
        Scene scene = club.getScenes().get(1);
        scene.setFolderUuid("ballads");
        scene.setSortIndex(2);
        scene.setIcon("fa-star");
        scene.setColor("#ff0000");
        scene.setColorAuto(false);
        scene.setDisplayColor("#ff0000");
        SceneFolder folder = new SceneFolder();
        folder.setUuid("ballads");
        folder.setName("Ballads");
        folder.setParentUuid("slow");
        folder.setSortIndex(3);
        club.setSceneFolders(List.of(folder));

        JsonNode project = new ObjectMapper().valueToTree(scenePlayerService().getProjects()).get(1);

        JsonNode written = project.get("scenes").get(1);
        assertEquals("ballads", written.get("folderUuid").asString());
        assertEquals(2, written.get("sortIndex").asDouble());
        assertEquals("fa-star", written.get("icon").asString());
        assertEquals("#ff0000", written.get("color").asString());
        assertFalse(written.get("colorAuto").asBoolean());
        assertEquals("#ff0000", written.get("displayColor").asString());
        assertTrue(project.get("scenes").get(0).get("colorAuto").asBoolean());

        JsonNode writtenFolder = project.get("sceneFolders").get(0);
        assertEquals("ballads", writtenFolder.get("uuid").asString());
        assertEquals("Ballads", writtenFolder.get("name").asString());
        assertEquals("slow", writtenFolder.get("parentUuid").asString());
        assertEquals(3, writtenFolder.get("sortIndex").asDouble());
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

    // Wait until the passed scenes are on, for at most the passed time
    private static void waitForScenes(DefaultScenePlayerService scenePlayerService, List<String> sceneUuids, long timeoutMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;

        while (!scenePlayerService.getActiveSceneUuids().equals(sceneUuids) && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }

        assertEquals(sceneUuids, scenePlayerService.getActiveSceneUuids());
    }

    @Test
    void playsASceneByItsNameOrItsUuidInsteadOfTheOnesOn() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-0", "Club Lights-1"));

        scenePlayerService.playScene("Club Lights", "Scene 1", ScenePlayerService.UNTIL_STOPPED);
        verify(designerService).playManualScenes(club, List.of("Club Lights-1"), 1.0);
        assertEquals(List.of("Club Lights-1"), scenePlayerService.getActiveSceneUuids());

        scenePlayerService.playScene("Club Lights", "Club Lights-0", ScenePlayerService.UNTIL_STOPPED);
        assertEquals(List.of("Club Lights-0"), scenePlayerService.getActiveSceneUuids());
    }

    @Test
    void refusesASceneTheProjectDoesNotHave() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        assertThrows(IllegalArgumentException.class, () -> scenePlayerService.playScene("Club Lights", "Strobe", ScenePlayerService.UNTIL_STOPPED));
        assertThrows(IllegalArgumentException.class, () -> scenePlayerService.playScene("Deleted", "Scene 0", ScenePlayerService.UNTIL_STOPPED));
    }

    @Test
    void turnsAScenePlayedForAWhileOffAtTheEndOfItsTime() throws Exception {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.playScene("Club Lights", "Scene 0", 200);
        assertEquals(List.of("Club Lights-0"), scenePlayerService.getActiveSceneUuids());

        waitForScenes(scenePlayerService, List.of(), 3000);

        // Every app hears of the end as well: at the start and at the end of the scene
        verify(notificationService, timeout(1000).times(2)).notifyClients(any(), any());
    }

    @Test
    void keepsTheScenesPickedWhileAScenePlayedForAWhile() throws Exception {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.playScene("Club Lights", "Scene 0", 200);
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-1"));
        Thread.sleep(500);

        assertEquals(List.of("Club Lights-1"), scenePlayerService.getActiveSceneUuids());
    }

    @Test
    void startsTheTimeOverWhenTheSameSceneIsPlayedAgain() throws Exception {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.playScene("Club Lights", "Scene 0", 600);
        Thread.sleep(400);
        scenePlayerService.playScene("Club Lights", "Scene 0", 600);
        Thread.sleep(400);

        // The end of the first time has passed
        assertEquals(List.of("Club Lights-0"), scenePlayerService.getActiveSceneUuids());

        waitForScenes(scenePlayerService, List.of(), 3000);

        // Both plays and the end are saved and sent to the apps (before the folder of the test is cleaned up)
        verify(notificationService, timeout(1000).times(3)).notifyClients(any(), any());
    }

    @Test
    void keepsAScenePlayedUntilStoppedOnAfterTheTimeOfTheOneBefore() throws Exception {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();

        scenePlayerService.playScene("Club Lights", "Scene 0", 200);
        scenePlayerService.playScene("Club Lights", "Scene 0", ScenePlayerService.UNTIL_STOPPED);
        Thread.sleep(500);
        assertEquals(List.of("Club Lights-0"), scenePlayerService.getActiveSceneUuids());

        // Another scene stops it, as does turning all scenes off
        scenePlayerService.playScene("Club Lights", "Scene 1", ScenePlayerService.UNTIL_STOPPED);
        assertEquals(List.of("Club Lights-1"), scenePlayerService.getActiveSceneUuids());
        scenePlayerService.releaseAll();
        assertEquals(List.of(), scenePlayerService.getActiveSceneUuids());
    }

    @Test
    void togglesTheBlackoutAndReleasesTheScenes() {
        DefaultScenePlayerService scenePlayerService = scenePlayerService();
        scenePlayerService.setActiveScenes("Club Lights", List.of("Club Lights-0"));

        scenePlayerService.toggleBlackout();
        assertTrue(scenePlayerService.isBlackout());
        scenePlayerService.toggleBlackout();
        assertFalse(scenePlayerService.isBlackout());

        scenePlayerService.releaseAll();
        assertEquals(List.of(), scenePlayerService.getActiveSceneUuids());
        assertEquals("Club Lights", scenePlayerService.getProjectName());
    }

}
