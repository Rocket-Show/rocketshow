package com.ascargon.rocketshow.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The initial setup, logging in and out and changing the password, like the web app does.
 */
class AuthApiTest extends ApiTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode setupRequest(String password, boolean wifiApEnabled, String wifiApPassword) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("language", "de");
        request.put("deviceName", "Stage left");
        request.put("password", password);
        request.put("wifiApEnabled", wifiApEnabled);
        request.put("wifiApPassword", wifiApPassword);
        request.put("tlsEnabled", false);
        return request;
    }

    private MvcResult postJson(String url, JsonNode body, MockHttpSession session) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (session != null) {
            request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode me(MockHttpSession session) throws Exception {
        var request = get("/api/auth/me");
        if (session != null) {
            request.session(session);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void asksANewUnitForTheSetup() throws Exception {
        JsonNode me = me(null);

        assertFalse(me.get("authenticated").asBoolean());
        assertFalse(me.get("passwordConfigured").asBoolean());
    }

    @Test
    void setsUpTheUnitAndLogsIn() throws Exception {
        MvcResult result = postJson("/api/auth/setup", setupRequest(ADMIN_PASSWORD, true, "wifi-password"), null);
        assertEquals(200, result.getResponse().getStatus());
        MockHttpSession admin = (MockHttpSession) result.getRequest().getSession(false);

        assertEquals("de", settingsService.getSettings().getLanguage());
        assertEquals("Stage left", settingsService.getSettings().getDeviceName());
        assertTrue(settingsService.getSettings().getWlanApEnable());
        assertEquals("wifi-password", settingsService.getSettings().getWlanApPassphrase());
        verify(settingsUpdateSystemService).update();

        JsonNode me = me(admin);
        assertTrue(me.get("authenticated").asBoolean());
        assertTrue(me.get("passwordConfigured").asBoolean());
        assertAllowed(get("/api/system/settings").session(admin));
    }

    @Test
    void refusesAPassphraseWhichLeavesTheAccessPointOpen() throws Exception {
        // E.g. the admin password of 6 characters, used for the access point as well
        assertEquals(400, postJson("/api/auth/setup", setupRequest("123456", true, "123456"), null).getResponse().getStatus());
        assertNull(settingsService.getSettings().getAdminPasswordHash());

        assertEquals(400, postJson("/api/auth/setup", setupRequest(ADMIN_PASSWORD, true, "x".repeat(64)), null).getResponse().getStatus());

        // Without access point, its passphrase doesn't matter
        assertEquals(200, postJson("/api/auth/setup", setupRequest("123456", false, "123456"), null).getResponse().getStatus());
    }

    @Test
    void refusesASetupWithoutPassword() throws Exception {
        assertEquals(400, postJson("/api/auth/setup", setupRequest(" ", false, ""), null).getResponse().getStatus());
    }

    @Test
    void givesTheAdminANewSession() throws Exception {
        setAdminPassword();

        // A session someone else might know, e.g. from a link
        MockHttpSession session = new MockHttpSession();
        String sessionId = session.getId();

        ObjectNode login = objectMapper.createObjectNode().put("password", ADMIN_PASSWORD);
        MvcResult result = postJson("/api/auth/login", login, session);

        assertEquals(200, result.getResponse().getStatus());
        assertNotEquals(sessionId, result.getRequest().getSession(false).getId());
    }

    @Test
    void refusesAWrongPassword() throws Exception {
        setAdminPassword();

        MvcResult result = postJson("/api/auth/login", objectMapper.createObjectNode().put("password", "wrong"), null);

        assertEquals(401, result.getResponse().getStatus());
        assertDenied(get("/api/system/settings").session((MockHttpSession) result.getRequest().getSession(true)));
    }

    @Test
    void endsTheSessionOnLogout() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        mockMvc.perform(post("/api/auth/logout").session(admin)).andExpect(status().isOk());

        assertDenied(get("/api/system/settings").session(admin));
    }

    @Test
    void changesThePassword() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        ObjectNode change = objectMapper.createObjectNode().put("oldPassword", "wrong").put("newPassword", "new-password");

        assertEquals(401, postJson("/api/auth/change-password", change, admin).getResponse().getStatus());

        change.put("oldPassword", ADMIN_PASSWORD).put("newPassword", " ");
        assertEquals(400, postJson("/api/auth/change-password", change, admin).getResponse().getStatus());

        change.put("newPassword", "new-password");
        assertEquals(200, postJson("/api/auth/change-password", change, admin).getResponse().getStatus());

        assertEquals(401, postJson("/api/auth/login", objectMapper.createObjectNode().put("password", ADMIN_PASSWORD), null).getResponse().getStatus());
        loginThrottleService.onSuccess();
        assertEquals(200, postJson("/api/auth/login", objectMapper.createObjectNode().put("password", "new-password"), null).getResponse().getStatus());
    }

    @Test
    void refusesToSaveAPassphraseWhichLeavesTheAccessPointOpen() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String settingsJson = mockMvc.perform(get("/api/system/settings").session(admin)).andReturn().getResponse().getContentAsString();
        ObjectNode settings = (ObjectNode) objectMapper.readTree(settingsJson);

        settings.put("wlanApPassphrase", "1234567");
        assertEquals(400, postJson("/api/system/settings", settings, admin).getResponse().getStatus());

        settings.put("wlanApPassphrase", "x".repeat(64));
        assertEquals(400, postJson("/api/system/settings", settings, admin).getResponse().getStatus());

        // Open on purpose
        settings.put("wlanApPassphrase", "");
        assertEquals(200, postJson("/api/system/settings", settings, admin).getResponse().getStatus());
    }

}
