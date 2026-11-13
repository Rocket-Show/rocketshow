package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.DeviceRegistrationService;
import com.ascargon.rocketshow.online.FakeOnlineApi;
import com.ascargon.rocketshow.online.OnlineApiClient;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.MAPPER;
import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.profile;
import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.version;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Syncing the fixture library of the unit with the online designer, page by page, in place of the zip it downloaded
 * before: the library changes only once all pages arrived, and stays as it was without internet.
 */
class FixtureLibrarySyncTest {

    @TempDir
    Path directory;

    private Path library;
    private FakeOnlineApi onlineApi;
    private AccountService accountService;
    private ObjectNode registration;
    private DefaultFixtureService fixtureService;

    @BeforeEach
    void setUp() throws Exception {
        // The library of the zip, as an updated unit has it
        library = directory.resolve("fixtures");
        FileUtils.copyDirectory(new File(getClass().getResource("/fixtures").toURI()), library.toFile());

        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setFixturePath("fixtures");
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        registration = MAPPER.createObjectNode();
        registration.put("kind", "rocketshow");
        registration.put("identifier", "hashed-identifier");
        DeviceRegistrationService deviceRegistrationService = mock(DeviceRegistrationService.class);
        when(deviceRegistrationService.getRegistration()).thenReturn(registration);

        accountService = mock(AccountService.class);
        when(accountService.getDeviceHeaders()).thenReturn(Map.of());

        onlineApi = new FakeOnlineApi();

        fixtureService = new DefaultFixtureService(settingsService, mock(FileFilterService.class), deviceRegistrationService, accountService, new OnlineApiClient(onlineApi.getUrl()));
    }

    @AfterEach
    void tearDown() {
        fixtureService.stopBackgroundSync();
        onlineApi.close();
    }

    // A page of the sync
    private static ObjectNode page(long cursor, boolean more, List<ObjectNode> versions, List<String> newProfiles) {
        ObjectNode page = MAPPER.createObjectNode();
        page.put("cursor", cursor);
        page.put("more", more);

        ArrayNode manufacturers = page.putArray("manufacturers");
        ArrayNode versionList = page.putArray("versions");
        ObjectNode profiles = page.putObject("profiles");

        for (ObjectNode version : versions) {
            versionList.add(version);
            manufacturers.addObject()
                    .put("shortName", version.get("manufacturerShortName").asString())
                    .put("name", version.get("manufacturerName").asString())
                    .put("website", "https://example.com");

            if (newProfiles.contains(version.get("uuid").asString())) {
                profiles.set(version.get("uuid").asString(), profile(version.get("name").asString()));
            }
        }

        return page;
    }

    private static long cursorOf(FakeOnlineApi.Request request) {
        return MAPPER.readTree(request.body()).get("cursor").asLong();
    }

    private static long sinceOf(FakeOnlineApi.Request request) {
        return MAPPER.readTree(request.body()).get("since").asLong();
    }

    // Answers the sync with the pages, by the cursor asked for
    private void answerSync(Map<Long, ObjectNode> pages) {
        answerSync(request -> FakeOnlineApi.Answer.json(pages.get(cursorOf(request)).toString()));
    }

    private void answerSync(Function<FakeOnlineApi.Request, FakeOnlineApi.Answer> handler) {
        onlineApi.answer("library-sync", handler);
    }

    private static final ObjectNode MH_100 = version("stairville/mh-100~1", "stairville/mh-100", "Stairville", "MH-100");
    private static final ObjectNode PAR = version("acme/par~2", "acme/par", "Acme", "Par");

    // Synced once, up to cursor 3
    private void syncFirstTime() throws Exception {
        answerSync(Map.of(0L, page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2"))));
        fixtureService.updateProfiles();
        onlineApi.clear();
    }

    private List<String> best() {
        return fixtureService.searchFixtures(null, null, null, null, null).stream().map(SearchFixtureTemplate::getUuid).toList();
    }

    private List<String> filesBesideTheLibrary() throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private List<String> filesOfTheLibrary() throws IOException {
        try (Stream<Path> paths = Files.walk(library)) {
            return paths.filter(Files::isRegularFile).map(path -> library.relativize(path).toString()).sorted().toList();
        }
    }

    private JsonNode libraryFile() {
        return MAPPER.readTree(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME).toFile());
    }

    @Test
    void replacesTheLibraryOfTheZipPageByPage() throws Exception {
        answerSync(Map.of(
                0L, page(2, true, List.of(MH_100), List.of("stairville/mh-100~1")),
                2L, page(3, false, List.of(PAR), List.of("acme/par~2"))
        ));

        fixtureService.updateProfiles();

        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
        assertEquals(List.of("acme/par~2.json", "library.json", "stairville/mh-100~1.json"), filesOfTheLibrary());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
        assertEquals("MH-100", MAPPER.readTree(fixtureService.getFixture("stairville/mh-100~1")).get("name").asString());

        JsonNode libraryFile = libraryFile();
        assertEquals(3, libraryFile.get("cursor").asLong());
        assertEquals("Stairville", libraryFile.get("manufacturers").get("stairville").get("name").asString());
        assertEquals("https://example.com", libraryFile.get("manufacturers").get("acme").get("website").asString());
        assertEquals(List.of(MH_100, PAR), List.of(libraryFile.get("versions").get(0), libraryFile.get("versions").get(1)));

        // The registration with each page
        List<FakeOnlineApi.Request> requests = onlineApi.getRequests("library-sync");
        assertEquals(2, requests.size());
        for (FakeOnlineApi.Request request : requests) {
            assertEquals("POST", request.method());
            assertTrue(request.header("Content-Type").startsWith("application/json"));
            assertEquals(registration, MAPPER.readTree(request.body()).get("device"));
        }
        assertEquals(List.of(0L, 2L), requests.stream().map(FixtureLibrarySyncTest::cursorOf).toList());

        // The profiles of every version created since the start of the sync, also on a later page
        assertEquals(List.of(0L, 0L), requests.stream().map(FixtureLibrarySyncTest::sinceOf).toList());
        assertEquals(List.of(), onlineApi.getRequests("fixture"));
    }

    @Test
    void continuesFromTheCursor() throws Exception {
        syncFirstTime();

        // A new version of the moving head, which became its best one
        ObjectNode newMh100 = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
        newMh100.put("versions", 2);
        ObjectNode oldMh100 = MH_100.deepCopy();
        oldMh100.put("best", false);
        oldMh100.put("bestUuid", "stairville/mh-100~4");
        oldMh100.put("currentOfl", false);
        oldMh100.put("versions", 2);
        answerSync(Map.of(3L, page(5, false, List.of(oldMh100, newMh100), List.of("stairville/mh-100~4"))));

        fixtureService.updateProfiles();

        assertEquals(List.of(3L), onlineApi.getRequests("library-sync").stream().map(FixtureLibrarySyncTest::cursorOf).toList());
        assertEquals(List.of(3L), onlineApi.getRequests("library-sync").stream().map(FixtureLibrarySyncTest::sinceOf).toList());
        // The changed version came without its profile, which the unit has
        assertEquals(List.of(), onlineApi.getRequests("fixture"));
        assertEquals(List.of("acme/par~2", "stairville/mh-100~4"), best());
        assertEquals(List.of("stairville/mh-100~4", "stairville/mh-100~1"), fixtureService.searchFixtures(null, "stairville/mh-100~1", null, null, null).stream().map(SearchFixtureTemplate::getUuid).toList());
        assertEquals(List.of("acme/par~2.json", "library.json", "stairville/mh-100~1.json", "stairville/mh-100~4.json"), filesOfTheLibrary());
        assertEquals(5, libraryFile().get("cursor").asLong());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    // The profile of a version, as fixture?uuid= of the online designer answers it
    private void answerProfiles() {
        onlineApi.answer("fixture", request -> {
            String uuid = URLDecoder.decode(request.query().substring("uuid=".length()), StandardCharsets.UTF_8);
            return FakeOnlineApi.Answer.json(uuid.equals("stairville/mh-100~4") ? profile("Loaded on its own").toString() : "null");
        });
    }

    @Test
    void loadsAProfileAVersionCameWithout() throws Exception {
        syncFirstTime();
        ObjectNode newMh100 = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
        answerSync(Map.of(3L, page(5, false, List.of(newMh100), List.of())));
        answerProfiles();

        fixtureService.updateProfiles();

        List<FakeOnlineApi.Request> requests = onlineApi.getRequests("fixture");
        assertEquals(1, requests.size());
        assertEquals("GET", requests.get(0).method());
        assertEquals("stairville/mh-100~4", URLDecoder.decode(requests.get(0).query().substring("uuid=".length()), StandardCharsets.UTF_8));
        assertEquals("Loaded on its own", MAPPER.readTree(fixtureService.getFixture("stairville/mh-100~4")).get("name").asString());
        assertEquals(5, libraryFile().get("cursor").asLong());
    }

    @Test
    void loadsTheProfilesAVersionCameWithoutWhenReplacingTheLibrary() throws Exception {
        // The library of the zip has none of the profiles of the versions
        ObjectNode newMh100 = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
        answerSync(Map.of(0L, page(3, false, List.of(newMh100, PAR), List.of("acme/par~2"))));
        answerProfiles();

        fixtureService.updateProfiles();

        assertEquals(1, onlineApi.getRequests("fixture").size());
        assertEquals(List.of("acme/par~2.json", "library.json", "stairville/mh-100~4.json"), filesOfTheLibrary());
    }

    @Test
    void keepsTheLibraryWhenAMissingProfileCannotBeLoaded() throws Exception {
        syncFirstTime();
        String libraryBefore = Files.readString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME));
        ObjectNode newMh100 = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
        answerSync(Map.of(3L, page(5, false, List.of(newMh100), List.of())));
        onlineApi.answer("fixture", request -> FakeOnlineApi.Answer.json(500, "{\"error\": \"internal\"}"));

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertEquals(libraryBefore, Files.readString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME)));
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void takesAVersionTheOnlineDesignerHasNoProfileOf() throws Exception {
        syncFirstTime();
        ObjectNode withoutProfile = version("acme/spot~6", "acme/spot", "Acme", "Spot");
        answerSync(Map.of(3L, page(5, false, List.of(withoutProfile), List.of())));
        answerProfiles();

        fixtureService.updateProfiles();

        assertTrue(best().contains("acme/spot~6"));
        assertNull(fixtureService.getFixture("acme/spot~6"));
    }

    @Test
    void keepsTheLibraryWhenAPageFails() throws Exception {
        syncFirstTime();
        String libraryBefore = Files.readString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME));
        ObjectNode newMh100 = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
        answerSync(request -> cursorOf(request) == 3
                ? FakeOnlineApi.Answer.json(page(4, true, List.of(newMh100), List.of("stairville/mh-100~4")).toString())
                : FakeOnlineApi.Answer.json(500, "{\"error\": \"internal\"}"));

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertEquals(libraryBefore, Files.readString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME)));
        assertEquals(List.of("acme/par~2.json", "library.json", "stairville/mh-100~1.json"), filesOfTheLibrary());
        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void keepsTheLibraryOfTheZipWithoutInternet() throws Exception {
        List<String> before = best();
        onlineApi.close();

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertTrue(before.contains("stairville/mh-100"));
        assertEquals(before, best());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void keepsTheLibraryWhenTheAnswerIsNoPage() throws Exception {
        List<String> before = best();
        answerSync(request -> new FakeOnlineApi.Answer(200, "text/html", "<html>Maintenance</html>".getBytes(), false));

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertEquals(before, best());
        assertFalse(Files.exists(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME)));
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void stopsWhenTheCursorStandsStill() throws Exception {
        syncFirstTime();
        answerSync(request -> FakeOnlineApi.Answer.json(page(3, true, List.of(), List.of()).toString()));

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertEquals(1, onlineApi.getRequests("library-sync").size());
        assertEquals(3, libraryFile().get("cursor").asLong());
    }

    @Test
    void unpacksACompressedPage() throws Exception {
        answerSync(request -> FakeOnlineApi.Answer.json(page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2")).toString()).gzipped());

        fixtureService.updateProfiles();

        assertTrue(onlineApi.getRequests("library-sync").get(0).header("Accept-Encoding").contains("gzip"));
        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
    }

    @Test
    void syncsAsTheLinkedAccount() throws Exception {
        when(accountService.getDeviceHeaders()).thenReturn(Map.of("Authorization", "Device device-token", "X-Device-Identifier", "hashed-identifier"));

        syncFirstTime();
        answerSync(Map.of(3L, page(3, false, List.of(), List.of())));
        fixtureService.updateProfiles();

        FakeOnlineApi.Request request = onlineApi.getRequests("library-sync").get(0);
        assertEquals("Device device-token", request.header("Authorization"));
        assertEquals("hashed-identifier", request.header("X-Device-Identifier"));
    }

    @Test
    void syncsWithoutALinkTheOnlineDesignerRefuses() throws Exception {
        // E.g. unlinked from "My devices" online
        Map<String, String> deviceHeaders = Map.of("Authorization", "Device device-token", "X-Device-Identifier", "hashed-identifier");
        when(accountService.getDeviceHeaders()).thenReturn(deviceHeaders);
        answerSync(request -> request.header("Authorization") != null
                ? FakeOnlineApi.Answer.json(401, "{\"error\": \"unauthorized\"}")
                : FakeOnlineApi.Answer.json(page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2")).toString()));

        fixtureService.updateProfiles();

        verify(accountService).forgetRefusedLink(deviceHeaders);
        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
    }

    @Test
    void writesNothingOutsideTheLibrary() throws Exception {
        ObjectNode page = page(3, false, List.of(MH_100), List.of("stairville/mh-100~1"));
        ((ArrayNode) page.get("versions")).add(version("../evil", "acme/evil", "Acme", "Evil"));
        ((ObjectNode) page.get("profiles")).set("../evil", profile("Evil"));
        ((ObjectNode) page.get("profiles")).set("acme/../../evil", profile("Evil"));
        answerSync(request -> FakeOnlineApi.Answer.json(page.toString()));

        fixtureService.updateProfiles();

        assertEquals(List.of("stairville/mh-100~1"), best());
        assertEquals(List.of("library.json", "stairville/mh-100~1.json"), filesOfTheLibrary());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void bringsTheLibraryBackWhenTheUnitWasSwitchedOffWhileReplacingIt() throws Exception {
        // The library had been moved aside, the new one not yet in its place
        List<String> before = best();
        Files.move(library, directory.resolve(".fixtures-old"));
        onlineApi.close();

        assertThrows(IOException.class, () -> fixtureService.updateProfiles());

        assertTrue(Files.isRegularFile(library.resolve("stairville/mh-100.json")));
        assertEquals(before, best());
    }

    @Test
    void removesTheLeftoversOfASyncBrokenOff() throws Exception {
        Files.createDirectories(directory.resolve(".fixtures-sync/acme"));
        Files.writeString(directory.resolve(".fixtures-sync/acme/stale~9.json"), "{\"name\": \"Stale\", \"modes\": []}");
        FileUtils.copyDirectory(library.toFile(), directory.resolve(".fixtures-old").toFile());
        answerSync(Map.of(0L, page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2"))));

        fixtureService.updateProfiles();

        assertEquals(List.of("acme/par~2.json", "library.json", "stairville/mh-100~1.json"), filesOfTheLibrary());
        assertEquals(List.of("fixtures"), filesBesideTheLibrary());
    }

    @Test
    void syncsANewUnit() throws Exception {
        FileUtils.deleteDirectory(library.toFile());
        assertEquals(List.of(), best());
        answerSync(Map.of(0L, page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2"))));

        fixtureService.updateProfiles();

        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
    }

    @Test
    void syncsInTheBackgroundAndAgainAfterAFailure() throws Exception {
        List<Long> answered = new ArrayList<>();
        answerSync(request -> {
            answered.add(cursorOf(request));
            return answered.size() == 1
                    ? FakeOnlineApi.Answer.json(503, "{\"error\": \"maintenance\"}")
                    : FakeOnlineApi.Answer.json(page(3, false, List.of(MH_100, PAR), List.of("stairville/mh-100~1", "acme/par~2")).toString());
        });
        fixtureService.firstSyncDelay = Duration.ZERO;
        fixtureService.syncRetryInterval = Duration.ofMillis(100);

        fixtureService.startBackgroundSync();

        for (int i = 0; i < 100 && !Files.exists(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME)); i++) {
            Thread.sleep(100);
        }
        assertEquals(List.of("acme/par~2", "stairville/mh-100~1"), best());
        assertEquals(2, onlineApi.getRequests("library-sync").size());
    }

}
