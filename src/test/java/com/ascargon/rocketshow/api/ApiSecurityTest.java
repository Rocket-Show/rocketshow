package com.ascargon.rocketshow.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Who may call which part of the API. Requests changing the system (e.g. reboot, factory reset, update) are only
 * sent where access must be denied, so they never run.
 */
class ApiSecurityTest extends ApiTest {

    @Test
    void publicEndpointsNeedNoLogin() throws Exception {
        setAdminPassword();

        assertAllowed(get("/api/auth/me"));
        assertAllowed(get("/api/system/health"));
        assertAllowed(get("/api/system/device-information"));
        assertAllowed(get("/api/system/update-state"));
    }

    @Test
    void apiNeedsLogin() throws Exception {
        setAdminPassword();

        assertDenied(get("/api/system/state"));
        assertDenied(get("/api/composition/list"));
        assertDenied(get("/api/file/get").param("name", "song.wav").param("type", "AUDIO"));
        assertDenied(get("/api/system/create-backup"));
        assertDenied(post("/api/system/reboot"));
        assertDenied(post("/api/system/shutdown"));
        assertDenied(post("/api/system/enable-ssh"));
        assertDenied(post("/api/system/execute-action").contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertDenied(post("/api/composition/delete").param("name", "Song"));
        assertDenied(post("/api/scenes/active").param("projectName", "Club").contentType(MediaType.APPLICATION_JSON).content("[]"));
    }

    @Test
    void apiKeyGivesAccessToTheApi() throws Exception {
        setAdminPassword();
        addApiKey();

        assertAllowed(get("/api/system/state").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertAllowed(get("/api/composition/list").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertAllowed(get("/api/scenes/projects").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
    }

    @Test
    void wrongApiKeyIsRejected() throws Exception {
        setAdminPassword();
        addApiKey();

        assertEquals(401, getStatus(get("/api/system/state").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, "wrong")));
    }

    @Test
    void adminEndpointsNeedTheAdmin() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        addApiKey();

        assertDenied(get("/api/system/settings").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(get("/api/lead-sheet/list").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/session/set-play-view-mode").param("value", "setlist").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/session/set-play-mode").param("value", "scenes").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/system/factory-reset").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/auth/change-password").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"oldPassword\": \"x\", \"newPassword\": \"y\"}"));

        assertAllowed(get("/api/system/settings").session(admin));
        assertAllowed(get("/api/lead-sheet/list").session(admin));
    }

    @Test
    void provisioningNeedsNoLoginBeforeTheSetup() throws Exception {
        assertAllowed(get("/api/system/current-version"));
        assertAllowed(post("/api/system/test"));
    }

    @Test
    void provisioningNeedsTheAdminAfterTheSetup() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        addApiKey();

        assertDenied(get("/api/system/current-version"));
        assertDenied(get("/api/system/current-version").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/system/update"));
        assertDenied(post("/api/system/update").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));

        assertAllowed(get("/api/system/current-version").session(admin));
    }

    @Test
    void loginNeedsTheAdminPassword() throws Exception {
        setAdminPassword();

        assertEquals(401, getStatus(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"wrong\"}")));
        assertEquals(401, getStatus(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"\"}")));
    }

    @Test
    void loginFailsBeforeTheSetup() throws Exception {
        assertEquals(401, getStatus(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"\"}")));
    }

    @Test
    void setupOnlyWorksOnce() throws Exception {
        String setup = "{\"password\": \"" + ADMIN_PASSWORD + "\", \"language\": \"en\"}";

        assertEquals(200, getStatus(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON).content(setup)));
        assertEquals(403, getStatus(post("/api/auth/setup").contentType(MediaType.APPLICATION_JSON).content(setup)));
    }

}
