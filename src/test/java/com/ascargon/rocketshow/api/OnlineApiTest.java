package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.online.DefaultAccountService;
import com.ascargon.rocketshow.online.FakeOnlineApi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The account of the online designer in the settings of the device, and the requests of the designer the device hands
 * on to the online designer as that account.
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

    private List<MockHttpServletRequestBuilder> forwardedRequests() {
        return List.of(
                get("/api/ai-looks"),
                post("/api/ai-looks").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/ai-fixtures"),
                multipart("/api/ai-fixtures").param("action", "lookup"),
                get("/api/fixture-vote?uuid=acme/par~2"),
                post("/api/fixture-vote").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/fixture-vote?uuid=acme/par~2&mode=8ch"),
                post("/api/fixture-offer").contentType(MediaType.APPLICATION_JSON).content("{}")
        );
    }

    @Test
    void answersThatTheDeviceIsNotLinked() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        for (MockHttpServletRequestBuilder request : forwardedRequests()) {
            mockMvc.perform(request.session(admin))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("device-not-linked"));
        }
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

        for (MockHttpServletRequestBuilder request : forwardedRequests()) {
            assertDenied(request.header(ApiKeyAuthFilter.API_KEY_HEADER_NAME, API_KEY));
        }
    }

    @Test
    void handsTheAiLooksOnAsTheAccount() throws Exception {
        MockHttpSession admin = linked();
        onlineApi.answer("ai-looks", request -> request.method().equals("GET")
                ? FakeOnlineApi.Answer.json("{\"available\": true, \"requestsLast24Hours\": 1}")
                : FakeOnlineApi.Answer.json(429, "{\"error\": \"ai-user-limit-reached\"}"));

        mockMvc.perform(get("/api/ai-looks").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"available\": true, \"requestsLast24Hours\": 1}"));

        // An error of the API as it is
        mockMvc.perform(post("/api/ai-looks").session(admin).contentType(MediaType.APPLICATION_JSON).content("{\"prompt\": \"A rock show\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("ai-user-limit-reached"));

        List<FakeOnlineApi.Request> requests = onlineApi.getRequests("ai-looks");
        assertEquals(List.of("GET", "POST"), requests.stream().map(FakeOnlineApi.Request::method).toList());
        for (FakeOnlineApi.Request request : requests) {
            assertEquals("Device device-token", request.header("Authorization"));
            assertTrue(request.header("X-Device-Identifier").matches("[0-9a-f]{64}"));
        }
        assertEquals("{\"prompt\": \"A rock show\"}", requests.get(1).bodyAsString());
        assertTrue(requests.get(1).header("Content-Type").startsWith("application/json"));
    }

    @Test
    void handsTheVotesOnWithTheirQuery() throws Exception {
        MockHttpSession admin = linked();
        onlineApi.answer("fixture-vote", request -> FakeOnlineApi.Answer.json("{\"modes\": []}"));

        mockMvc.perform(get("/api/fixture-vote?uuid=stairville/mh-100~12").session(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/fixture-vote").session(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\": \"stairville/mh-100~12\", \"mode\": \"14ch\", \"good\": false, \"comment\": \"Channel 6 is the strobe\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/fixture-vote?uuid=stairville/mh-100~12&mode=14ch").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"modes\": []}"));

        List<FakeOnlineApi.Request> requests = onlineApi.getRequests("fixture-vote");
        assertEquals(List.of("GET", "POST", "DELETE"), requests.stream().map(FakeOnlineApi.Request::method).toList());
        assertEquals("uuid=stairville/mh-100~12", requests.get(0).query());
        assertTrue(requests.get(1).bodyAsString().contains("Channel 6 is the strobe"));
        assertEquals("uuid=stairville/mh-100~12&mode=14ch", requests.get(2).query());
    }

    @Test
    void handsAManualOnToTheAi() throws Exception {
        MockHttpSession admin = linked();
        onlineApi.answer("ai-fixtures", request -> FakeOnlineApi.Answer.json("{\"version\": {\"uuid\": \"acme/par~13\"}}"));
        byte[] manual = "%PDF-1.4 the manual of the par".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/ai-fixtures").file(new MockMultipartFile("manual", "par manual.pdf", "application/pdf", manual))
                        .param("action", "generate")
                        .param("model", "Par")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.uuid").value("acme/par~13"));

        FakeOnlineApi.Request request = onlineApi.getRequests("ai-fixtures").get(0);
        String contentType = request.header("Content-Type");
        assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
        String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());
        String body = request.bodyAsString();

        assertEquals(String.valueOf(request.body().length), request.header("Content-Length"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"action\"\r\n\r\ngenerate\r\n"), body);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"model\"\r\n\r\nPar\r\n"), body);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"manual\"; filename=\"par manual.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4 the manual of the par\r\n"), body);
        assertTrue(body.endsWith("--" + boundary + "--\r\n"), body);
    }

    @Test
    void forgetsALinkTheOnlineDesignerRefuses() throws Exception {
        // E.g. unlinked from "My devices" online
        MockHttpSession admin = linked();
        onlineApi.answer("ai-looks", request -> FakeOnlineApi.Answer.json(401, "{\"error\": \"unauthorized\"}"));

        MvcResult result = mockMvc.perform(get("/api/ai-looks").session(admin)).andReturn();

        assertEquals(401, result.getResponse().getStatus());
        assertEquals("{\"error\": \"unauthorized\"}", result.getResponse().getContentAsString());
        mockMvc.perform(get("/api/account").session(admin)).andExpect(jsonPath("$.linked").value(false));
    }

}
