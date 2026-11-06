package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.audio.AudioBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audio buses the designer offers a pixel effect to follow, by their names.
 */
class DesignerAudioBusesApiTest extends ApiTest {

    private List<AudioBus> audioBuses;

    @AfterEach
    void restoreAudioBuses() {
        if (audioBuses != null) {
            settingsService.getSettings().setAudioBusList(audioBuses);
        }
    }

    @Test
    void listsTheAudioBusesByTheirNames() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        audioBuses = settingsService.getSettings().getAudioBusList();

        AudioBus music = new AudioBus();
        music.setUuid("music");
        music.setName("Music");
        AudioBus click = new AudioBus();
        click.setUuid("click");
        click.setName("Click");
        settingsService.getSettings().setAudioBusList(new ArrayList<>(List.of(music, click)));

        mockMvc.perform(get("/api/audio-buses").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].uuid").value("music"))
                .andExpect(jsonPath("$[0].name").value("Music"))
                .andExpect(jsonPath("$[1].name").value("Click"))
                .andExpect(jsonPath("$[0].audioDevice").doesNotExist());
    }

}
