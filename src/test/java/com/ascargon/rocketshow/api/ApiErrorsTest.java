package com.ascargon.rocketshow.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How the API answers requests it can't fulfill.
 */
class ApiErrorsTest extends ApiTest {

    @Test
    void answersAMissingFileWithNotFound() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        // e.g. the media file of the designer template, deleted in the media library
        mockMvc.perform(get("/api/file/get").param("name", "deleted-in-the-media-library.wav").param("type", "AUDIO").session(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/project").param("name", "Deleted project").session(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersAFileNameLeavingItsFolderWithBadRequest() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(get("/api/file/get").param("name", "../../settings.xml").param("type", "AUDIO").session(admin))
                .andExpect(status().isBadRequest());
    }

}
