package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.AtomicFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The account of the online designer the device is linked to (see the plan of the fixture library in the designer,
 * section 8). A linked device has a token of its own, which the API takes only along with the identifier of the
 * device, and the AI and the votes of the designer work through it.
 */
@Service
public class DefaultAccountService implements AccountService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultAccountService.class);

    // The token and the name of the account, in the base path. Not in the settings, which the web app gets, and not
    // in the backups (see DefaultBackupService): a backup restored on another device must not arrive linked.
    public final static String FILE_NAME = "account.json";

    private final static Duration TIMEOUT = Duration.ofSeconds(30);

    private final static String AUTHORIZATION_PREFIX = "Device ";

    private final SettingsService settingsService;
    private final DeviceIdentifierService deviceIdentifierService;
    private final OnlineApiClient onlineApiClient;

    private final ObjectMapper mapper = new ObjectMapper();

    public DefaultAccountService(SettingsService settingsService, DeviceIdentifierService deviceIdentifierService, OnlineApiClient onlineApiClient) {
        this.settingsService = settingsService;
        this.deviceIdentifierService = deviceIdentifierService;
        this.onlineApiClient = onlineApiClient;
    }

    private File getFile() {
        return new File(settingsService.getSettings().getBasePath(), FILE_NAME);
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    // The token of the account, null while the device isn't linked
    private synchronized String readToken() {
        File file = getFile();

        if (!file.isFile()) {
            return null;
        }

        try {
            String token = text(mapper.readTree(file).get("token"));
            return token == null || token.isBlank() ? null : token;
        } catch (Exception e) {
            logger.error("Could not read the account of the device from {}", file, e);
            return null;
        }
    }

    @Override
    public synchronized AccountStatus getStatus() {
        if (readToken() == null) {
            return new AccountStatus(false, null);
        }

        try {
            return new AccountStatus(true, text(mapper.readTree(getFile()).get("username")));
        } catch (Exception e) {
            return new AccountStatus(true, null);
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(field.getValue() == null ? "" : field.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    @Override
    public synchronized AccountStatus link(String email, String password) throws IOException, OnlineApiException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("email", email);
        fields.put("password", password);
        fields.put("identifier", deviceIdentifierService.getIdentifier());
        fields.put("name", settingsService.getSettings().getDeviceName());
        fields.put("kind", "rocketshow");

        OnlineApiClient.Response response = onlineApiClient.send("POST", "device-link",
                Map.of("Content-Type", "application/x-www-form-urlencoded"), HttpRequest.BodyPublishers.ofString(form(fields)), TIMEOUT);

        if (!response.isSuccessful()) {
            logger.info("The online designer refused to link the device: HTTP {}", response.status());
            throw new OnlineApiException(response);
        }

        JsonNode answer;

        try {
            answer = mapper.readTree(response.body());
        } catch (Exception e) {
            throw new IOException("The online designer answered no account", e);
        }

        String token = text(answer.get("token"));

        if (token == null || token.isBlank()) {
            throw new IOException("The online designer answered no token");
        }

        ObjectNode account = mapper.createObjectNode();
        account.put("token", token);
        account.put("username", text(answer.get("username")));

        File file = getFile();
        AtomicFile.writeString(file, mapper.writeValueAsString(account));

        // Only for the app, whoever else uses the unit
        try {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            logger.debug("Could not restrict the permissions of {}", file, e);
        }

        logger.info("The device has been linked to an account of the online designer");

        return getStatus();
    }

    private Map<String, String> getDeviceHeaders(String token) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", AUTHORIZATION_PREFIX + token);
        headers.put("X-Device-Identifier", deviceIdentifierService.getIdentifier());
        return headers;
    }

    @Override
    public Map<String, String> getDeviceHeaders() throws IOException {
        String token = readToken();
        return token == null ? Map.of() : getDeviceHeaders(token);
    }

    private synchronized void delete() {
        try {
            Files.deleteIfExists(getFile().toPath());
        } catch (IOException e) {
            logger.error("Could not delete the account of the device", e);
        }
    }

    @Override
    public synchronized void unlink() {
        String token = readToken();

        if (token == null) {
            delete();
            return;
        }

        // Without internet the account keeps the device in "My devices" until it's unlinked there, but it can't be
        // used from this device anymore
        try {
            OnlineApiClient.Response response = onlineApiClient.send("POST", "device-unlink", getDeviceHeaders(token), null, TIMEOUT);

            if (!response.isSuccessful()) {
                logger.warn("The online designer didn't unlink the device: HTTP {}", response.status());
            }
        } catch (IOException e) {
            logger.warn("Could not unlink the device online", e);
        }

        delete();
        logger.info("The device has been unlinked from its account of the online designer");
    }

    @Override
    public synchronized void forgetRefusedLink(Map<String, String> deviceHeaders) {
        String token = readToken();

        if (token != null && (AUTHORIZATION_PREFIX + token).equals(deviceHeaders.get("Authorization"))) {
            logger.info("The online designer doesn't take the link of the device anymore: the device isn't linked anymore");
            delete();
        }
    }

}
