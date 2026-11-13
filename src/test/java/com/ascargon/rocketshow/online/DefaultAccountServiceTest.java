package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Linking the device to an account of the online designer: the token it gets is kept in a file of its own, and is
 * sent along with the identifier of the device.
 */
class DefaultAccountServiceTest {

    private final static String IDENTIFIER = "a".repeat(64);

    @TempDir
    Path basePath;

    private FakeOnlineApi onlineApi;
    private Settings settings;
    private DefaultAccountService accountService;

    @BeforeEach
    void setUp() throws Exception {
        onlineApi = new FakeOnlineApi();

        settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settings.setDeviceName("Spark of the band");
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        DeviceIdentifierService deviceIdentifierService = mock(DeviceIdentifierService.class);
        when(deviceIdentifierService.getIdentifier()).thenReturn(IDENTIFIER);

        accountService = new DefaultAccountService(settingsService, deviceIdentifierService, new OnlineApiClient(onlineApi.getUrl()));
    }

    @AfterEach
    void tearDown() {
        onlineApi.close();
    }

    private static Map<String, String> form(String body) {
        Map<String, String> fields = new HashMap<>();

        for (String field : body.split("&")) {
            String[] parts = field.split("=", 2);
            fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }

        return fields;
    }

    private void linkable() {
        onlineApi.answer("device-link", request -> FakeOnlineApi.Answer.json("{\"token\": \"device-token\", \"username\": \"band@example.com\"}"));
    }

    @Test
    void isNotLinkedAtFirst() throws Exception {
        assertFalse(accountService.getStatus().linked());
        assertEquals(Map.of(), accountService.getDeviceHeaders());
    }

    @Test
    void linksTheDevice() throws Exception {
        linkable();

        AccountStatus status = accountService.link("band@example.com", "p&ss=word");

        assertTrue(status.linked());
        assertEquals("band@example.com", status.username());

        // Like the login of the online designer, as a form, with what tells the device
        FakeOnlineApi.Request request = onlineApi.getRequests("device-link").get(0);
        assertEquals("POST", request.method());
        assertTrue(request.header("Content-Type").startsWith("application/x-www-form-urlencoded"));
        assertEquals(Map.of("email", "band@example.com", "password", "p&ss=word", "identifier", IDENTIFIER, "name", "Spark of the band", "kind", "rocketshow"), form(request.bodyAsString()));

        // The token in a file of its own, not the password
        String account = Files.readString(basePath.resolve(DefaultAccountService.FILE_NAME));
        assertTrue(account.contains("device-token"));
        assertFalse(account.contains("p&ss=word"));

        assertEquals(Map.of("Authorization", "Device device-token", "X-Device-Identifier", IDENTIFIER), accountService.getDeviceHeaders());
    }

    @Test
    void handsOnARefusal() {
        onlineApi.answer("device-link", request -> FakeOnlineApi.Answer.json(400, "{\"error\": \"wrong-credentials\"}"));

        OnlineApiException exception = assertThrows(OnlineApiException.class, () -> accountService.link("band@example.com", "wrong"));

        assertEquals(400, exception.getResponse().status());
        assertEquals("{\"error\": \"wrong-credentials\"}", exception.getResponse().bodyAsString());
        assertFalse(accountService.getStatus().linked());
        assertFalse(Files.exists(basePath.resolve(DefaultAccountService.FILE_NAME)));
    }

    @Test
    void staysUnlinkedWithoutInternet() throws Exception {
        onlineApi.close();

        assertThrows(IOException.class, () -> accountService.link("band@example.com", "password"));
        assertFalse(accountService.getStatus().linked());
    }

    @Test
    void unlinksTheDeviceOnline() throws Exception {
        linkable();
        onlineApi.answer("device-unlink", request -> FakeOnlineApi.Answer.json("{}"));
        accountService.link("band@example.com", "password");

        accountService.unlink();

        FakeOnlineApi.Request request = onlineApi.getRequests("device-unlink").get(0);
        assertEquals("POST", request.method());
        assertEquals("Device device-token", request.header("Authorization"));
        assertEquals(IDENTIFIER, request.header("X-Device-Identifier"));
        assertFalse(accountService.getStatus().linked());
        assertFalse(Files.exists(basePath.resolve(DefaultAccountService.FILE_NAME)));
    }

    @Test
    void unlinksTheDeviceWithoutInternet() throws Exception {
        linkable();
        accountService.link("band@example.com", "password");
        onlineApi.close();

        accountService.unlink();

        assertFalse(accountService.getStatus().linked());
    }

    @Test
    void forgetsALinkTheOnlineDesignerRefuses() throws Exception {
        linkable();
        accountService.link("band@example.com", "password");
        Map<String, String> refused = Map.of("Authorization", "Device old-token", "X-Device-Identifier", IDENTIFIER);

        // Linked anew meanwhile: kept
        accountService.forgetRefusedLink(refused);
        assertTrue(accountService.getStatus().linked());

        accountService.forgetRefusedLink(accountService.getDeviceHeaders());
        assertFalse(accountService.getStatus().linked());
        assertEquals(List.of(), onlineApi.getRequests("device-unlink"));
    }

    @Test
    void isNotLinkedWithABrokenAccountFile() throws Exception {
        Files.writeString(basePath.resolve(DefaultAccountService.FILE_NAME), "{\"token\": ");

        assertFalse(accountService.getStatus().linked());
        assertEquals(Map.of(), accountService.getDeviceHeaders());
    }

}
