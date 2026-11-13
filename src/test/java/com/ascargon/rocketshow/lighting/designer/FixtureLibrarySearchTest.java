package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.DeviceRegistrationService;
import com.ascargon.rocketshow.online.OnlineApiClient;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.MAPPER;
import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.profile;
import static com.ascargon.rocketshow.lighting.designer.LibraryVersions.version;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The search of the designer in the library the unit synced: the same answers as the API of the online designer.
 */
class FixtureLibrarySearchTest {

    @TempDir
    Path directory;

    private Path library;
    private DefaultFixtureService fixtureService;

    // Five versions of the moving head: one confirmed by three users and the best, one confirmed but hidden, the
    // current one of the Open Fixture Library and an older one, and one with problems
    private final ObjectNode confirmed = version("stairville/mh-100~7", "stairville/mh-100", "Stairville", "MH-100");
    private final ObjectNode hidden = version("stairville/mh-100~8", "stairville/mh-100", "Stairville", "MH-100");
    private final ObjectNode currentOfl = version("stairville/mh-100~4", "stairville/mh-100", "Stairville", "MH-100");
    private final ObjectNode olderOfl = version("stairville/mh-100~1", "stairville/mh-100", "Stairville", "MH-100");
    private final ObjectNode problem = version("stairville/mh-100~9", "stairville/mh-100", "Stairville", "MH-100");

    private final ObjectNode par = version("acme/par~2", "acme/par", "Acme", "Par");
    private final ObjectNode flatPar = version("cameo/flat-par~5", "cameo/flat-par", "Cameo", "Flat PAR");
    private final ObjectNode hiddenFixture = version("adj/junk~3", "adj/junk", "ADJ", "Junk");

    // Stored under its key of before, the fixture was renamed since
    private final ObjectNode renamed = version("adj/scanner~10", "american-dj/scanner", "American DJ", "Scanner");

    @BeforeEach
    void setUp() throws Exception {
        for (ObjectNode version : List.of(confirmed, hidden, currentOfl, olderOfl, problem)) {
            version.put("versions", 4);
            version.put("best", false);
            version.put("bestUuid", "stairville/mh-100~7");
            version.put("currentOfl", false);
        }

        confirmed.put("origin", "ai");
        confirmed.put("state", "confirmed");
        confirmed.put("confirmations", 3);
        confirmed.put("usage", 1);
        confirmed.put("best", true);
        confirmed.put("parentUuid", "stairville/mh-100~4");
        confirmed.putObject("manual").put("url", "https://example.com/mh-100.pdf");
        ((ObjectNode) confirmed.get("modeStates").get(0)).put("state", "confirmed").put("good", 3);

        hidden.put("origin", "user");
        hidden.put("state", "confirmed");
        hidden.put("confirmations", 1);
        hidden.put("hidden", true);

        currentOfl.put("currentOfl", true);
        currentOfl.put("usage", 10);

        olderOfl.put("usage", 20);
        olderOfl.put("created", "2024-09-09T10:00:00Z");
        olderOfl.put("retired", true);

        problem.put("origin", "ai");
        problem.put("state", "problem");
        problem.put("problems", 2);
        problem.put("usage", 50);

        par.put("mainCategory", "Color Changer");
        par.put("currentOfl", false);
        hiddenFixture.put("hidden", true);

        library = directory.resolve("fixtures");
        ObjectNode libraryFile = MAPPER.createObjectNode();
        libraryFile.put("cursor", 1234);
        libraryFile.putObject("manufacturers").putObject("stairville").put("name", "Stairville").put("website", "https://example.com");
        ArrayNode versions = libraryFile.putArray("versions");

        for (ObjectNode version : List.of(olderOfl, currentOfl, confirmed, hidden, problem, par, flatPar, hiddenFixture, renamed)) {
            versions.add(version);

            Path profile = library.resolve(version.get("uuid").asString() + ".json");
            Files.createDirectories(profile.getParent());
            Files.writeString(profile, profile(version.get("uuid").asString()).toString());
        }

        Files.writeString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME), libraryFile.toString());

        Settings settings = new Settings();
        settings.setBasePath(directory + File.separator);
        settings.setFixturePath("fixtures");
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        fixtureService = new DefaultFixtureService(settingsService, mock(FileFilterService.class), mock(DeviceRegistrationService.class),
                mock(AccountService.class), new OnlineApiClient("http://127.0.0.1:1/designer/api/"));
    }

    private List<String> search(String uuid, String fixture, String manufacturerShortName, String name, String mainCategory) {
        return fixtureService.searchFixtures(uuid, fixture, manufacturerShortName, name, mainCategory).stream().map(SearchFixtureTemplate::getUuid).toList();
    }

    @Test
    void listsTheBestVersionOfEachFixtureWhichIsNotHidden() {
        // By manufacturer (Acme, American DJ, Cameo, Stairville) and name
        assertEquals(List.of("acme/par~2", "adj/scanner~10", "cameo/flat-par~5", "stairville/mh-100~7"), search(null, null, null, null, null));
    }

    @Test
    void filtersLikeTheOnlineDesigner() {
        assertEquals(List.of("acme/par~2", "cameo/flat-par~5"), search(null, null, null, "%par%", null));
        assertEquals(List.of("acme/par~2"), search(null, null, null, "P_r", null));
        assertEquals(List.of("stairville/mh-100~7"), search(null, null, "stair%", null, null));
        assertEquals(List.of("acme/par~2"), search(null, null, null, null, "color changer"));
        assertEquals(List.of(), search(null, null, "acme", "MH%", null));
    }

    @Test
    void findsAVersionByItsUuid() {
        assertEquals(List.of("stairville/mh-100~8"), search("stairville/mh-100~8", null, null, null, null));
        assertEquals(List.of("stairville/mh-100~1"), search("stairville/mh-100~1", null, null, null, null));
        assertEquals(List.of(), search("stairville/mh-100~99", null, null, null, null));
    }

    @Test
    void answersAPlainUuidWithTheCurrentVersionOfTheOpenFixtureLibrary() throws Exception {
        // E.g. the copy of a profile in a project of before the sync
        assertEquals(List.of("stairville/mh-100~4"), search("stairville/mh-100", null, null, null, null));
        assertEquals("stairville/mh-100~4", MAPPER.readTree(fixtureService.getFixture("stairville/mh-100")).get("name").asString());

        // Else with the best version
        assertEquals(List.of("acme/par~2"), search("acme/par", null, null, null, null));
        assertEquals(List.of(), search("acme/unknown", null, null, null, null));
        assertNull(fixtureService.getFixture("acme/unknown"));
    }

    @Test
    void listsTheVersionsOfAFixtureBestFirst() {
        List<String> ranking = List.of("stairville/mh-100~7", "stairville/mh-100~8", "stairville/mh-100~4", "stairville/mh-100~1", "stairville/mh-100~9");

        assertEquals(ranking, search(null, "stairville/mh-100~1", null, null, null));
        assertEquals(ranking, search(null, "stairville/mh-100", null, null, null));
        assertEquals(List.of("adj/scanner~10"), search(null, "adj/scanner~10", null, null, null));
        assertEquals(List.of("adj/scanner~10"), search(null, "american-dj/scanner", null, null, null));
        assertEquals(List.of(), search(null, "acme/unknown", null, null, null));
    }

    @Test
    void readsTheProfileOfAVersion() throws Exception {
        assertEquals("stairville/mh-100~9", MAPPER.readTree(fixtureService.getFixture("stairville/mh-100~9")).get("name").asString());
        assertNull(fixtureService.getFixture("stairville/mh-100~99"));
    }

    @Test
    void answersEveryFieldOfTheOnlineDesigner() {
        SearchFixtureTemplate found = fixtureService.searchFixtures("stairville/mh-100~7", null, null, null, null).get(0);
        JsonNode answer = MAPPER.valueToTree(found);

        assertEquals(confirmed, answer);
        assertEquals("https://example.com/mh-100.pdf", found.getManual().getUrl());
        assertEquals("confirmed", found.getModeStates().get(0).getState());
        assertEquals(3, found.getModeStates().get(0).getGood());

        // As null, like the online designer answers it
        JsonNode open = MAPPER.valueToTree(fixtureService.searchFixtures("acme/par~2", null, null, null, null).get(0));
        assertTrue(open.has("parentUuid"));
        assertTrue(open.get("manual").isNull());
    }

    @Test
    void readsTheOtherVersionsBesideOneItCannotRead() throws Exception {
        ObjectNode libraryFile = (ObjectNode) MAPPER.readTree(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME).toFile());
        ObjectNode broken = ((ArrayNode) libraryFile.get("versions")).addObject();
        broken.put("uuid", "acme/broken~11");
        broken.put("usage", "many");
        Files.writeString(library.resolve(DefaultFixtureService.LIBRARY_FILE_NAME), libraryFile.toString());

        List<String> found = search(null, null, null, null, null);

        assertFalse(found.contains("acme/broken~11"));
        assertTrue(found.contains("acme/par~2"));
    }

}
