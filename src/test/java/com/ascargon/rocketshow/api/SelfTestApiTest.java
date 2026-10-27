package com.ascargon.rocketshow.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The self-test through the API, on the unit of the tests (no sound card, display, GPIO or DMX): like at the factory
 * and by an admin.
 */
class SelfTestApiTest extends ApiTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static JsonNode check(JsonNode result, String name) {
        for (JsonNode check : result.get("checks")) {
            if (check.get("name").asText().equals(name)) {
                return check;
            }
        }
        throw new AssertionError("No check " + name + " in " + result);
    }

    @Test
    void aNewUnitTestsItselfWithoutLogin() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/system/test")).andReturn();
        assertEquals(200, mvcResult.getResponse().getStatus(), mvcResult.getResponse().getContentAsString());
        JsonNode result = objectMapper.readTree(mvcResult.getResponse().getContentAsString());

        List<String> names = new ArrayList<>();
        result.get("checks").forEach(check -> names.add(check.get("name").asText()));
        assertTrue(names.containsAll(List.of("health", "storage", "audio", "video", "midi-out", "midi-loopback", "gpio-out", "gpio-loopback", "dmx")), names.toString());

        assertEquals("PASSED", check(result, "storage").get("result").asText(), result.toString());
        // Nothing the unit of the tests doesn't have fails
        assertEquals("SKIPPED", check(result, "video").get("result").asText());
        assertEquals("SKIPPED", check(result, "gpio-loopback").get("result").asText());
        assertEquals("PASSED", result.get("result").asText(), result.toString());
        assertTrue(result.get("startedAt").asText().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ"), result.toString());
        assertTrue(result.get("durationMillis").asLong() > 0, result.toString());
    }

    @Test
    void anExpectedLoopbackFailsWithoutCable() throws Exception {
        MockHttpSession admin = loginAsAdmin();

        MvcResult mvcResult = mockMvc.perform(post("/api/system/test").session(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expect\": [\"gpio-loopback\"]}")).andReturn();
        assertEquals(200, mvcResult.getResponse().getStatus(), mvcResult.getResponse().getContentAsString());
        JsonNode result = objectMapper.readTree(mvcResult.getResponse().getContentAsString());

        assertEquals("FAILED", result.get("result").asText());
        assertEquals("FAILED", check(result, "gpio-loopback").get("result").asText());
        assertEquals("Expected, but skipped: No GPIO loopback in the request", check(result, "gpio-loopback").get("details").asText());
    }

    @Test
    void refusesAnUnknownCheck() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/system/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expect\": [\"midi-loop\"]}")).andReturn();

        assertEquals(400, mvcResult.getResponse().getStatus());
        assertTrue(mvcResult.getResponse().getContentAsString().contains("There is no check midi-loop"), mvcResult.getResponse().getContentAsString());
    }

    @Test
    void refusesALoopbackOnOnePin() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/system/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gpioLoopback\": [{\"out\": 22, \"in\": 22}]}")).andReturn();

        assertEquals(400, mvcResult.getResponse().getStatus());
        assertTrue(mvcResult.getResponse().getContentAsString().contains("GPIO 22 is used by more than one loopback"), mvcResult.getResponse().getContentAsString());
    }

}
