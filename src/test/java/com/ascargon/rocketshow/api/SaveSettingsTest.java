package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.settings.ApiKey;
import com.ascargon.rocketshow.settings.Settings;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving the settings like the web app does: load them, change them, send them back.
 */
class SaveSettingsTest extends ApiTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode loadSettings(MockHttpSession admin) throws Exception {
        String json = mockMvc.perform(get("/api/system/settings").session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return (ObjectNode) objectMapper.readTree(json);
    }

    private void saveSettings(MockHttpSession admin, JsonNode settings) throws Exception {
        mockMvc.perform(post("/api/system/settings").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(settings.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void storesOnlyTheHashOfNewApiKeys() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        ObjectNode settings = loadSettings(admin);
        ObjectNode apiKey = settings.putArray("apiKeyList").addObject();
        apiKey.put("uuid", "new-unit");
        apiKey.put("key", "new-api-key");
        saveSettings(admin, settings);

        ApiKey storedApiKey = settingsService.getSettings().getApiKeyList().getFirst();
        assertNull(storedApiKey.getKey());
        assertTrue(new BCryptPasswordEncoder().matches("new-api-key", storedApiKey.getKeyHash()));
        assertAllowed(get("/api/system/state").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, "new-api-key"));
    }

    @Test
    void keepsTheHashOfExistingApiKeys() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String keyHash = addApiKey().getKeyHash();

        // The web app gets neither the key nor its hash
        ObjectNode settings = loadSettings(admin);
        assertFalse(settings.get("apiKeyList").get(0).has("keyHash"));
        saveSettings(admin, settings);

        assertEquals(keyHash, settingsService.getSettings().getApiKeyList().getFirst().getKeyHash());
        assertAllowed(get("/api/system/state").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
    }

    @Test
    void removesDeletedApiKeys() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        addApiKey();

        ObjectNode settings = loadSettings(admin);
        settings.putArray("apiKeyList");
        saveSettings(admin, settings);

        assertTrue(settingsService.getSettings().getApiKeyList().isEmpty());
        assertEquals(401, getStatus(get("/api/system/state").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY)));
    }

    @Test
    void keepsTheAdminPassword() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String adminPasswordHash = settingsService.getSettings().getAdminPasswordHash();

        ObjectNode settings = loadSettings(admin);
        assertFalse(settings.has("adminPasswordHash"));
        settings.put("adminPasswordHash", "replaced");
        saveSettings(admin, settings);

        assertEquals(adminPasswordHash, settingsService.getSettings().getAdminPasswordHash());
    }

    // Under the names the web app uses for them: fields the unit doesn't know are ignored
    @Test
    void keepsTheOtherUnitsOfActionsAndMidiRoutings() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        try {
            ObjectNode settings = loadSettings(admin);
            ObjectNode action = settings.putArray("actionTriggerMidiList").addObject().putObject("actionTriggerMidiNoteOn")
                    .putArray("actionList").addObject().putObject("actionTransport");
            action.put("transportActionType", "PLAY");
            action.putArray("remoteDeviceNames").add("Unit B");
            ObjectNode midiRouting = settings.putArray("remoteMidiRoutingList").addObject();
            midiRouting.put("midiDestination", "REMOTE");
            midiRouting.putArray("remoteDeviceNameList").add("Unit B");
            saveSettings(admin, settings);

            Settings saved = settingsService.getSettings();
            assertEquals(List.of("Unit B"), saved.getActionTriggerMidiList().getFirst().getActionList().getFirst().getRemoteDeviceNames());
            assertEquals(List.of("Unit B"), saved.getRemoteMidiRoutingList().getFirst().getRemoteDeviceNameList());

            ObjectNode loaded = loadSettings(admin);
            assertEquals("Unit B", loaded.get("actionTriggerMidiList").get(0).get("actionTriggerMidiNoteOn").get("actionList").get(0)
                    .get("actionTransport").get("remoteDeviceNames").get(0).stringValue());
            assertEquals("Unit B", loaded.get("remoteMidiRoutingList").get(0).get("remoteDeviceNameList").get(0).stringValue());
        } finally {
            settingsService.getSettings().getActionTriggerMidiList().clear();
            settingsService.getSettings().getRemoteMidiRoutingList().clear();
        }
    }

    @Test
    void appliesTheSettingsToTheSystem() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        saveSettings(admin, loadSettings(admin));

        verify(settingsUpdateSystemService).update();
    }

}
