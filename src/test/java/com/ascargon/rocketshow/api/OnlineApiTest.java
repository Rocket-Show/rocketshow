package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.online.DefaultAccountService;
import com.ascargon.rocketshow.online.FakeOnlineApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account of the online designer in the settings of the device.
 */
class OnlineApiTest extends ApiTest {

    private static final FakeOnlineApi onlineApi = startOnlineApi();
    private static final Path directory = createDirectory();

    private static FakeOnlineApi startOnlineApi() {
        try {
            return new FakeOnlineApi();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createDirectory() {
        try {
            return Files.createTempDirectory("rocketshow-online-api-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void useTheFakeOnlineApi(DynamicPropertyRegistry registry) {
        registry.add("rocketshow.online-api-url", onlineApi::getUrl);
        registry.add("rocketshow.directory", directory::toString);
    }

    @AfterEach
    void unlink() throws Exception {
        Files.deleteIfExists(directory.resolve(DefaultAccountService.FILE_NAME));
        onlineApi.clear();
    }

    @AfterAll
    static void stopOnlineApi() {
        onlineApi.close();
    }

    private MockHttpSession linked() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        onlineApi.answer("device-link", request -> FakeOnlineApi.Answer.json("{\"token\": \"device-token\", \"username\": \"band@example.com\"}"));

        mockMvc.perform(post("/api/account/login").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"band@example.com\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.username").value("band@example.com"));

        return admin;
    }

    @Test
    void linksAndUnlinksTheDevice() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(get("/api/account").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));

        linked();

        mockMvc.perform(get("/api/account").session(admin))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.username").value("band@example.com"));
        assertTrue(Files.exists(directory.resolve(DefaultAccountService.FILE_NAME)));

        // The token is in a file of its own, not in the settings the web app gets
        String settings = mockMvc.perform(get("/api/system/settings").session(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(settings.contains("device-token"));

        onlineApi.answer("device-unlink", request -> FakeOnlineApi.Answer.json("{}"));
        mockMvc.perform(post("/api/account/logout").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linked").value(false));

        FakeOnlineApi.Request unlink = onlineApi.getRequests("device-unlink").get(0);
        assertEquals("Device device-token", unlink.header("Authorization"));
        assertTrue(unlink.header("X-Device-Identifier").matches("[0-9a-f]{64}"));
    }

    @Test
    void handsOnAWrongPassword() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        onlineApi.answer("device-link", request -> FakeOnlineApi.Answer.json(400, "{\"error\": \"wrong-credentials\"}"));

        mockMvc.perform(post("/api/account/login").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"band@example.com\", \"password\": \"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("wrong-credentials"));

        mockMvc.perform(get("/api/account").session(admin)).andExpect(jsonPath("$.linked").value(false));
    }

    @Test
    void onlyTheAdminUsesTheAccount() throws Exception {
        setAdminPassword();
        addApiKey();

        assertDenied(get("/api/account"));
        assertDenied(get("/api/account").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        assertDenied(post("/api/account/login").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"x\", \"password\": \"y\"}"));
        assertDenied(post("/api/account/logout").header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
    }

}
