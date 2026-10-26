package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.composition.SetComposition;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.play.CompositionPlayer;
import com.ascargon.rocketshow.play.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Selecting and playing compositions through the API, like the web app, triggers and other units do.
 */
class TransportApiTest extends ApiTest {

    // Names the web app has to send encoded
    private final static String SONG = "Rock & Roll + C#1";
    private final static String OTHER_SONG = "50% more?";

    @Autowired
    CompositionService compositionService;

    @Autowired
    SetService setService;

    @Autowired
    PlayerService playerService;

    private MockHttpSession admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAsAdmin();

        for (String name : new String[]{SONG, OTHER_SONG}) {
            Composition composition = new Composition();
            composition.setName(name);
            ActionTriggerComposition later = new ActionTriggerComposition();
            later.setPositionMillis(600_000);
            composition.getActionTriggerList().add(later);
            compositionService.saveComposition(composition);
        }

        Set set = new Set();
        set.setName("Transport test set");
        for (String name : new String[]{SONG, OTHER_SONG}) {
            SetComposition setComposition = new SetComposition();
            setComposition.setName(name);
            set.getSetCompositionList().add(setComposition);
        }
        compositionService.saveSet(set);
    }

    @AfterEach
    void cleanUp() throws Exception {
        playerService.stop();
        playerService.loadSetAndComposition("");
        compositionService.deleteSet("Transport test set");
        compositionService.deleteComposition(SONG);
        compositionService.deleteComposition(OTHER_SONG);
    }

    @Test
    void selectsAndReadsCompositionsWithSymbolsInTheirName() throws Exception {
        mockMvc.perform(post("/api/transport/set-composition-name").param("name", SONG).session(admin)).andExpect(status().isOk());
        assertEquals(SONG, playerService.getCompositionName());

        mockMvc.perform(get("/api/composition").param("name", OTHER_SONG).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(OTHER_SONG));
    }

    @Test
    void refusesACompositionIndexOutsideTheSet() throws Exception {
        mockMvc.perform(post("/api/set/load").param("name", "Transport test set").session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/transport/set-composition-index").param("index", "1").session(admin)).andExpect(status().isOk());
        assertEquals(OTHER_SONG, playerService.getCompositionName());

        // E.g. from a web app showing a set changed meanwhile
        mockMvc.perform(post("/api/transport/set-composition-index").param("index", "2").session(admin)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/transport/set-composition-index").param("index", "-1").session(admin)).andExpect(status().isBadRequest());

        // Still on the composition, stepping through the set works
        assertEquals(OTHER_SONG, playerService.getCompositionName());
        assertEquals(1, setService.getCurrentCompositionIndex());
        mockMvc.perform(post("/api/transport/previous-composition").session(admin)).andExpect(status().isOk());
        assertEquals(SONG, playerService.getCompositionName());
    }

    @Test
    void refusesACompositionIndexWithoutSet() throws Exception {
        playerService.loadSetAndComposition("");

        mockMvc.perform(post("/api/transport/set-composition-index").param("index", "0").session(admin)).andExpect(status().isBadRequest());
    }

    @Test
    void playsNothingOnAUnitWithoutTheComposition() throws Exception {
        // Another unit plays a composition this one doesn't have
        mockMvc.perform(post("/api/transport/set-composition-name").param("name", SONG).session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/transport/load").param("name", "Only on the main unit").session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/transport/play").session(admin)).andExpect(status().isOk());

        assertNull(playerService.getCompositionName());
        assertEquals(CompositionPlayer.PlayState.STOPPED, playerService.getPlayState());
    }

    @Test
    void playsAndStops() throws Exception {
        mockMvc.perform(post("/api/transport/load").param("name", SONG).session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/transport/play").session(admin)).andExpect(status().isOk());
        assertEquals(CompositionPlayer.PlayState.PLAYING, playerService.getPlayState());

        mockMvc.perform(post("/api/transport/seek").param("positionMillis", "1000").session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/transport/pause").session(admin)).andExpect(status().isOk());
        assertEquals(CompositionPlayer.PlayState.PAUSED, playerService.getPlayState());

        mockMvc.perform(post("/api/transport/stop").session(admin)).andExpect(status().isOk());
        assertEquals(CompositionPlayer.PlayState.STOPPED, playerService.getPlayState());
    }

}
