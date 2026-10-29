package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.ScenePlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lighting the stage with the scenes of a designer project through the API, like other devices do (e.g. a controller
 * asking for one scene for a while or a blackout).
 */
class ScenesApiTest extends ApiTest {

    private final static String PROJECT = "Scenes API test";

    @Autowired
    DesignerService designerService;

    @Autowired
    ScenePlayerService scenePlayerService;

    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAsAdmin();

        designerService.saveProject("""
                {
                  "version": 10, "name": "%s", "masterDimmerValue": 1,
                  "fixtureProfiles": [], "fixtures": [], "presetFixtures": [], "presets": [], "compositions": [],
                  "scenes": [
                    {"uuid": "warm", "name": "Warm wash", "presetUuids": []},
                    {"uuid": "cool", "name": "Cool wash", "presetUuids": []}
                  ]
                }
                """.formatted(PROJECT));
    }

    @AfterEach
    void tearDown() {
        // The scenes are remembered with the session of the unit, which the other tests share
        scenePlayerService.setBlackout(false);
        scenePlayerService.releaseAll();
        designerService.deleteProjectByName(PROJECT);
    }

    @Test
    void playsOneSceneByItsNameInsteadOfTheOnesOn() throws Exception {
        mockMvc.perform(post("/api/scenes/active").session(admin).param("projectName", PROJECT)
                        .contentType(MediaType.APPLICATION_JSON).content("[\"warm\", \"cool\"]"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/scenes/play").session(admin).param("projectName", PROJECT).param("scene", "Cool wash"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/state").session(admin))
                .andExpect(jsonPath("$.sceneProjectName").value(PROJECT))
                .andExpect(jsonPath("$.activeSceneUuids.length()").value(1))
                .andExpect(jsonPath("$.activeSceneUuids[0]").value("cool"));
    }

    @Test
    void turnsTheSceneOffAfterTheTimeAsked() throws Exception {
        mockMvc.perform(post("/api/scenes/play").session(admin).param("projectName", PROJECT).param("scene", "warm")
                        .param("durationMillis", "200"))
                .andExpect(status().isOk());
        assertEquals(List.of("warm"), scenePlayerService.getActiveSceneUuids());

        long end = System.currentTimeMillis() + 3000;
        while (!scenePlayerService.getActiveSceneUuids().isEmpty() && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }

        assertEquals(List.of(), scenePlayerService.getActiveSceneUuids());
    }

    @Test
    void refusesASceneTheProjectDoesNotHave() throws Exception {
        mockMvc.perform(post("/api/scenes/play").session(admin).param("projectName", PROJECT).param("scene", "Strobe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void switchesTheBlackoutAndReleasesTheScenes() throws Exception {
        mockMvc.perform(post("/api/scenes/play").session(admin).param("projectName", PROJECT).param("scene", "warm"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/scenes/toggle-blackout").session(admin)).andExpect(status().isOk());
        assertTrue(scenePlayerService.isBlackout());

        mockMvc.perform(post("/api/scenes/blackout").session(admin).param("value", "false")).andExpect(status().isOk());
        assertFalse(scenePlayerService.isBlackout());

        mockMvc.perform(post("/api/scenes/release").session(admin)).andExpect(status().isOk());
        assertEquals(List.of(), scenePlayerService.getActiveSceneUuids());
    }

}
