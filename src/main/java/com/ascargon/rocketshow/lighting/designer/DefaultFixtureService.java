package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.DeviceRegistrationService;
import com.ascargon.rocketshow.online.OnlineApiClient;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.AtomicFile;
import com.ascargon.rocketshow.util.FileFilterService;
import com.ascargon.rocketshow.util.FileNames;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The fixture library of the unit: a copy of the one of the online designer, which the designer of the unit searches.
 * It is synced page by page with the online designer (library-sync of its API), which keeps every version of a
 * profile: a file per version, named by its uuid, and library.json with what the search needs of each version (see
 * the plan of the fixture library in the designer, docs/fixture-library.md). The registration of the unit comes along
 * with each sync.
 */
@Service
public class DefaultFixtureService implements FixtureService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultFixtureService.class);

    // What the search needs of every version and the cursor of the last sync. Written last: the library changes only
    // once a sync is complete.
    public final static String LIBRARY_FILE_NAME = "library.json";

    // The manufacturers of the library downloaded as a zip until this release, which the first sync replaces
    private final static String MANUFACTURERS_FILE_NAME = "manufacturers.json";

    // A page holds a few hundred versions
    private final static Duration SYNC_PAGE_TIMEOUT = Duration.ofSeconds(60);

    // The library is synced shortly after the start, without holding it up (the network of a unit may need a moment
    // after booting), then once a day. A sync which failed (e.g. no internet at a gig) is tried again within the hour,
    // so a unit going online later gets the library the same day. Package visible for the tests.
    Duration firstSyncDelay = Duration.ofSeconds(30);
    Duration syncInterval = Duration.ofDays(1);
    Duration syncRetryInterval = Duration.ofHours(1);

    private final SettingsService settingsService;
    private final FileFilterService fileFilterService;
    private final DeviceRegistrationService deviceRegistrationService;
    private final AccountService accountService;
    private final OnlineApiClient onlineApiClient;

    private final ObjectMapper mapper = new ObjectMapper();

    private final File libraryDirectory;

    // The library as the search sees it, read from the files once after each sync
    private volatile Library searchLibrary;

    private final ScheduledExecutorService syncScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fixture-library-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean backgroundSyncStarted = new AtomicBoolean();

    public DefaultFixtureService(
            SettingsService settingsService,
            FileFilterService fileFilterService,
            DeviceRegistrationService deviceRegistrationService,
            AccountService accountService,
            OnlineApiClient onlineApiClient
    ) {
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;
        this.deviceRegistrationService = deviceRegistrationService;
        this.accountService = accountService;
        this.onlineApiClient = onlineApiClient;

        libraryDirectory = new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getFixturePath()).getAbsoluteFile();
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private static int number(Integer value) {
        return value == null ? 0 : value;
    }

    // The versions of the library, found by their uuid and by their fixture, and its manufacturers
    private static class Library {

        private final List<SearchFixtureTemplate> versions;
        private final List<FixtureManufacturer> manufacturers;
        private final Map<String, SearchFixtureTemplate> byUuid = new HashMap<>();
        private final Map<String, List<SearchFixtureTemplate>> byFixture = new HashMap<>();

        private Library(List<SearchFixtureTemplate> versions, List<FixtureManufacturer> manufacturers) {
            this.versions = versions;
            this.manufacturers = manufacturers.stream().sorted(Comparator.comparing(FixtureManufacturer::shortName)).toList();

            for (SearchFixtureTemplate version : versions) {
                byUuid.putIfAbsent(version.getUuid(), version);
                byFixture.computeIfAbsent(version.getFixture() == null ? version.getUuid() : version.getFixture(), fixture -> new ArrayList<>()).add(version);
            }
        }

        // The version with the passed uuid. A plain uuid names a fixture and no version: clients of before the sync
        // (e.g. the copy of a profile in an older project) get its current version of the Open Fixture Library, else
        // its best version, like from the online designer.
        private SearchFixtureTemplate find(String uuid) {
            SearchFixtureTemplate version = byUuid.get(uuid);

            if (version != null) {
                return version;
            }

            List<SearchFixtureTemplate> versionsOfFixture = byFixture.getOrDefault(uuid, List.of());

            return versionsOfFixture.stream().filter(versionOfFixture -> isTrue(versionOfFixture.getCurrentOfl())).findFirst()
                    .orElse(versionsOfFixture.stream().filter(versionOfFixture -> isTrue(versionOfFixture.getBest())).findFirst().orElse(null));
        }

        // All versions of the fixture of the passed version, or of the passed fixture, the hidden ones as well, best
        // first
        private List<SearchFixtureTemplate> versionsOf(String uuid) {
            SearchFixtureTemplate version = byUuid.get(uuid);
            String fixture = version != null && version.getFixture() != null ? version.getFixture() : uuid;

            return byFixture.getOrDefault(fixture, List.of()).stream().sorted(DefaultFixtureService::compareRanking).toList();
        }

        // The best version of each fixture which isn't hidden
        private List<SearchFixtureTemplate> best() {
            return versions.stream()
                    .filter(version -> isTrue(version.getBest()) && !isTrue(version.getHidden()))
                    .sorted(DefaultFixtureService::compareNames)
                    .toList();
        }

    }

    // By manufacturer and name, like the online designer orders its search
    static int compareNames(SearchFixtureTemplate a, SearchFixtureTemplate b) {
        int result = String.CASE_INSENSITIVE_ORDER.compare(text(a.getManufacturerName()), text(b.getManufacturerName()));

        if (result == 0) {
            result = String.CASE_INSENSITIVE_ORDER.compare(text(a.getName()), text(b.getName()));
        }

        return result == 0 ? text(a.getUuid()).compareTo(text(b.getUuid())) : result;
    }

    // Confirmed first, then the current version of the Open Fixture Library while it is open, the other open ones,
    // then the problems
    private static int rankingGroup(SearchFixtureTemplate version) {
        if ("confirmed".equals(version.getState())) {
            return 0;
        }

        if ("problem".equals(version.getState())) {
            return 3;
        }

        return isTrue(version.getCurrentOfl()) ? 1 : 2;
    }

    // The order of the versions of a fixture, as the online designer ranks them (the plan of the fixture library,
    // section 11): the best version first, then by their state (the confirmed ones with the most good votes less bad
    // ones first), then by their usage, then the newer one first
    static int compareRanking(SearchFixtureTemplate a, SearchFixtureTemplate b) {
        int result = Boolean.compare(isTrue(b.getBest()), isTrue(a.getBest()));

        if (result == 0) {
            result = Integer.compare(rankingGroup(a), rankingGroup(b));
        }

        if (result == 0 && rankingGroup(a) == 0) {
            result = Integer.compare(number(b.getConfirmations()) - number(b.getProblems()), number(a.getConfirmations()) - number(a.getProblems()));
        }

        if (result == 0) {
            result = Integer.compare(number(b.getUsage()), number(a.getUsage()));
        }

        if (result == 0) {
            result = text(b.getCreated()).compareTo(text(a.getCreated()));
        }

        return result == 0 ? text(a.getUuid()).compareTo(text(b.getUuid())) : result;
    }

    // SQL LIKE as the online designer filters, with a collation ignoring the case: % for any text, _ for one
    // character, a backslash to take either as it is
    static boolean like(String value, String pattern) {
        if (value == null) {
            return false;
        }

        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < pattern.length(); i++) {
            char character = pattern.charAt(i);

            if (character == '\\' && i + 1 < pattern.length()) {
                i++;
                regex.append(Pattern.quote(String.valueOf(pattern.charAt(i))));
            } else if (character == '%') {
                regex.append(".*");
            } else if (character == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }

        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL).matcher(value).matches();
    }

    private static boolean matches(SearchFixtureTemplate version, String manufacturerShortName, String name, String mainCategory) {
        return (manufacturerShortName == null || like(version.getManufacturerShortName(), manufacturerShortName))
                && (name == null || like(version.getName(), name))
                && (mainCategory == null || mainCategory.equalsIgnoreCase(version.getMainCategory()));
    }

    private Library getLibrary() {
        Library library = searchLibrary;

        if (library == null) {
            library = readLibrary();
            searchLibrary = library;
        }

        return library;
    }

    private Library readLibrary() {
        File libraryFile = new File(libraryDirectory, LIBRARY_FILE_NAME);

        if (!libraryFile.isFile()) {
            return readZipLibrary();
        }

        List<SearchFixtureTemplate> versions = new ArrayList<>();
        List<FixtureManufacturer> manufacturers = new ArrayList<>();

        try {
            JsonNode library = mapper.readTree(libraryFile);

            for (Map.Entry<String, JsonNode> manufacturer : library.path("manufacturers").properties()) {
                manufacturers.add(new FixtureManufacturer(manufacturer.getKey(), text(manufacturer.getValue().get("name")), text(manufacturer.getValue().get("website"))));
            }

            for (JsonNode version : library.path("versions")) {
                try {
                    SearchFixtureTemplate searchFixtureTemplate = mapper.treeToValue(version, SearchFixtureTemplate.class);

                    if (searchFixtureTemplate.getUuid() != null) {
                        versions.add(searchFixtureTemplate);
                    }
                } catch (Exception e) {
                    // A version which can't be read must not hide the others from the search
                    logger.error("Could not read a version of the fixture library: {}", version, e);
                }
            }
        } catch (Exception e) {
            logger.error("Could not read the fixture library {}", libraryFile, e);
        }

        return new Library(versions, manufacturers);
    }

    // The library downloaded as a zip until this release: a profile per fixture, named by its plain uuid. Searched
    // until the first sync replaces it, e.g. on a unit updated without internet.
    private Library readZipLibrary() {
        List<SearchFixtureTemplate> versions = new ArrayList<>();
        List<FixtureManufacturer> manufacturers = new ArrayList<>();
        File manufacturersFile = new File(libraryDirectory, MANUFACTURERS_FILE_NAME);

        if (!manufacturersFile.isFile()) {
            return new Library(versions, manufacturers);
        }

        try {
            Manufacturers zipManufacturers = mapper.readValue(manufacturersFile, Manufacturers.class);

            logger.debug("Found {} manufacturers", zipManufacturers.getManufacturers().size());

            for (Map.Entry<String, Manufacturer> entry : zipManufacturers.getManufacturers().entrySet()) {
                String name = entry.getValue() == null ? null : entry.getValue().getName();
                manufacturers.add(new FixtureManufacturer(entry.getKey(), name, entry.getValue() == null ? null : entry.getValue().getWebsite()));
                readZipManufacturer(entry.getKey(), name, versions);
            }
        } catch (Exception e) {
            logger.error("Could not read the manufacturers of the fixture library", e);
        }

        return new Library(versions, manufacturers);
    }

    private void readZipManufacturer(String manufacturerShortName, String manufacturerName, List<SearchFixtureTemplate> versions) {
        File[] files = new File(libraryDirectory, manufacturerShortName).listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (!file.isFile() || fileFilterService.filterFile(file.getName()) || !file.getName().endsWith(".json")) {
                continue;
            }

            try {
                // read like the designer reads the profile, which it takes from here
                DesignerJson.MAPPER.readValue(file, FixtureProfile.class);
                JsonNode profile = mapper.readTree(file);

                String uuid = manufacturerShortName + "/" + file.getName().substring(0, file.getName().length() - ".json".length());

                SearchFixtureTemplate searchFixtureTemplate = new SearchFixtureTemplate();
                searchFixtureTemplate.setUuid(uuid);
                searchFixtureTemplate.setFixture(uuid);
                searchFixtureTemplate.setManufacturerShortName(manufacturerShortName);
                searchFixtureTemplate.setManufacturerName(manufacturerName);
                searchFixtureTemplate.setName(text(profile.get("name")));
                searchFixtureTemplate.setMainCategory(text(profile.path("categories").path(0)));
                // The zip held the profiles of the Open Fixture Library, one per fixture
                searchFixtureTemplate.setOrigin("ofl");
                searchFixtureTemplate.setVersions(1);
                searchFixtureTemplate.setBest(true);
                searchFixtureTemplate.setBestUuid(uuid);
                searchFixtureTemplate.setHidden(false);
                searchFixtureTemplate.setRetired(false);

                versions.add(searchFixtureTemplate);
            } catch (Exception e) {
                // A profile, which can't be read, must not hide the others from the search
                logger.error("Could not parse fixture {}", file.getPath(), e);
            }
        }
    }

    @Override
    public List<SearchFixtureTemplate> searchFixtures(String uuid, String fixture, String manufacturerShortName, String name, String mainCategory) {
        Library library = getLibrary();
        List<SearchFixtureTemplate> versions;

        if (uuid != null) {
            SearchFixtureTemplate version = library.find(uuid);
            versions = version == null ? List.of() : List.of(version);
        } else if (fixture != null) {
            versions = library.versionsOf(fixture);
        } else {
            versions = library.best();
        }

        return versions.stream().filter(version -> matches(version, manufacturerShortName, name, mainCategory)).toList();
    }

    @Override
    public List<FixtureManufacturer> getManufacturers() {
        return getLibrary().manufacturers;
    }

    // The uuid is "<manufacturer>/<fixture>" or "<manufacturer>/<fixture>~<version>" and must not leave the fixture
    // directory
    private String checkUuid(String uuid) {
        String[] parts = uuid == null ? new String[0] : uuid.split("/", -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid fixture uuid '" + uuid + "'");
        }

        FileNames.check(parts[0]);
        FileNames.check(parts[1]);

        return uuid;
    }

    private boolean isValidUuid(String uuid) {
        try {
            checkUuid(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String getFixture(String uuid) throws IOException {
        checkUuid(uuid);

        // The file of the version, of the version a plain uuid stands for, or of a profile of the zip
        SearchFixtureTemplate version = getLibrary().find(uuid);
        File file = new File(libraryDirectory, checkUuid(version == null ? uuid : version.getUuid()) + ".json");

        return file.isFile() ? FileUtils.readFileToString(file, StandardCharsets.UTF_8) : null;
    }

    // The library as it was synced last, null if there is none (e.g. the library of the zip or a new unit)
    private ObjectNode readLibraryFile() {
        File file = new File(libraryDirectory, LIBRARY_FILE_NAME);

        if (!file.isFile()) {
            return null;
        }

        try {
            JsonNode library = mapper.readTree(file);
            return library instanceof ObjectNode objectNode ? objectNode : null;
        } catch (Exception e) {
            logger.error("Could not read the fixture library {}, it is synced anew", file, e);
            return null;
        }
    }

    private OnlineApiClient.Response postSyncPage(byte[] body, Map<String, String> deviceHeaders) throws IOException {
        Map<String, String> headers = new HashMap<>(deviceHeaders);
        headers.put("Content-Type", "application/json");

        return onlineApiClient.send("POST", "library-sync", headers, HttpRequest.BodyPublishers.ofByteArray(body), SYNC_PAGE_TIMEOUT);
    }

    // The next page of the sync. The profiles come for the versions created after the cursor the sync started from
    // (since), also on a later page than the one they were created on. A linked unit syncs as its account, so that
    // its usage of the profiles counts for their ranking.
    private JsonNode requestSyncPage(long cursor, long since, ObjectNode device) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        request.put("cursor", cursor);
        request.put("since", since);
        request.set("device", device);
        byte[] body = mapper.writeValueAsBytes(request);

        Map<String, String> deviceHeaders = accountService.getDeviceHeaders();
        OnlineApiClient.Response response = postSyncPage(body, deviceHeaders);

        // E.g. unlinked from "My devices" online: the library is synced all the same
        if (response.status() == 401 && !deviceHeaders.isEmpty()) {
            accountService.forgetRefusedLink(deviceHeaders);
            response = postSyncPage(body, Map.of());
        }

        if (!response.isSuccessful()) {
            throw new IOException("The online designer answered the sync of the fixture library with HTTP " + response.status());
        }

        JsonNode page;

        try {
            page = mapper.readTree(response.body());
        } catch (Exception e) {
            throw new IOException("The online designer answered the sync of the fixture library with no page", e);
        }

        if (!page.isObject()) {
            throw new IOException("The online designer answered the sync of the fixture library with no page");
        }

        return page;
    }

    // Loads the profile of a version on its own, into the passed file. Nothing, when the online designer has none.
    private void loadProfile(String uuid, File file) throws IOException {
        OnlineApiClient.Response response = onlineApiClient.send("GET", "fixture?uuid=" + URLEncoder.encode(uuid, StandardCharsets.UTF_8),
                Map.of(), null, SYNC_PAGE_TIMEOUT);

        if (!response.isSuccessful()) {
            throw new IOException("The online designer answered the profile " + uuid + " with HTTP " + response.status());
        }

        JsonNode profile;

        try {
            profile = mapper.readTree(response.body());
        } catch (Exception e) {
            throw new IOException("The online designer answered the profile " + uuid + " with no profile", e);
        }

        if (profile == null || !profile.isObject()) {
            logger.warn("The online designer has no profile of the version {} of the fixture library", uuid);
            return;
        }

        Files.createDirectories(file.getParentFile().toPath());
        AtomicFile.writeString(file, mapper.writeValueAsString(profile));
    }

    // Moves the profiles of a sync into the library, where they are new
    private static void moveProfiles(File syncDirectory, File libraryDirectory) throws IOException {
        if (!syncDirectory.isDirectory()) {
            return;
        }

        List<Path> profiles;

        try (Stream<Path> paths = Files.walk(syncDirectory.toPath())) {
            profiles = paths.filter(Files::isRegularFile).toList();
        }

        for (Path profile : profiles) {
            Path target = libraryDirectory.toPath().resolve(syncDirectory.toPath().relativize(profile));
            Files.createDirectories(target.getParent());
            Files.move(profile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public synchronized void updateProfiles() throws IOException {
        // What a sync gets is kept beside the library until it is complete: a sync breaking off (e.g. on a stage
        // network) must not leave the unit with a part of a library
        File parentDirectory = libraryDirectory.getParentFile();
        File syncDirectory = new File(parentDirectory, "." + libraryDirectory.getName() + "-sync");
        File oldDirectory = new File(parentDirectory, "." + libraryDirectory.getName() + "-old");

        // Switched off while replacing the library the last time
        if (!libraryDirectory.exists() && oldDirectory.exists()) {
            Files.move(oldDirectory.toPath(), libraryDirectory.toPath());
        }

        // Left by a sync which broke off
        FileUtils.deleteDirectory(syncDirectory);
        FileUtils.deleteDirectory(oldDirectory);

        // Without a library synced before (the library of the zip or none), the whole library is synced from the start
        // and replaces the directory
        ObjectNode lastLibrary = readLibraryFile();
        boolean fromStart = lastLibrary == null;
        long cursor = fromStart ? 0 : lastLibrary.path("cursor").asLong(0);
        ObjectNode manufacturers = mapper.createObjectNode();
        Map<String, JsonNode> versions = new LinkedHashMap<>();

        if (!fromStart) {
            for (Map.Entry<String, JsonNode> manufacturer : lastLibrary.path("manufacturers").properties()) {
                manufacturers.set(manufacturer.getKey(), manufacturer.getValue());
            }

            for (JsonNode version : lastLibrary.path("versions")) {
                String uuid = text(version.get("uuid"));

                if (uuid != null) {
                    versions.put(uuid, version);
                }
            }
        }

        try {
            ObjectNode device = deviceRegistrationService.getRegistration();
            long since = cursor;
            boolean more = true;
            int pages = 0;
            Set<String> arrivedVersions = new LinkedHashSet<>();

            while (more) {
                JsonNode page = requestSyncPage(cursor, since, device);
                long nextCursor = page.path("cursor").asLong(cursor);
                more = page.path("more").asBoolean(false);
                pages++;

                for (JsonNode manufacturer : page.path("manufacturers")) {
                    String shortName = text(manufacturer.get("shortName"));

                    if (shortName != null) {
                        ObjectNode entry = manufacturers.putObject(shortName);
                        entry.put("name", text(manufacturer.get("name")));
                        entry.put("website", text(manufacturer.get("website")));
                    }
                }

                // What changed about a version replaces what the unit had
                for (JsonNode version : page.path("versions")) {
                    String uuid = text(version.get("uuid"));

                    if (uuid == null || !isValidUuid(uuid)) {
                        logger.warn("Skipping a version of the fixture library with the uuid '{}'", uuid);
                        continue;
                    }

                    versions.put(uuid, version);
                    arrivedVersions.add(uuid);
                }

                // A profile never changes, only the new ones come
                for (Map.Entry<String, JsonNode> profile : page.path("profiles").properties()) {
                    if (!isValidUuid(profile.getKey())) {
                        logger.warn("Skipping the profile of the fixture library with the uuid '{}'", profile.getKey());
                        continue;
                    }

                    File file = new File(syncDirectory, profile.getKey() + ".json");
                    Files.createDirectories(file.getParentFile().toPath());
                    AtomicFile.writeString(file, mapper.writeValueAsString(profile.getValue()));
                }

                // A cursor standing still would ask for the same page forever
                if (more && nextCursor <= cursor) {
                    throw new IOException("The sync of the fixture library got no further than cursor " + cursor);
                }

                cursor = nextCursor;
            }

            // A version which came without its profile, which the unit doesn't have either (a library replaced keeps
            // none of its files): its profile on its own, before the library knows the version
            for (String uuid : arrivedVersions) {
                File profile = new File(syncDirectory, uuid + ".json");

                if (!profile.isFile() && (fromStart || !new File(libraryDirectory, uuid + ".json").isFile())) {
                    loadProfile(uuid, profile);
                }
            }

            ObjectNode library = mapper.createObjectNode();
            library.put("cursor", cursor);
            library.set("manufacturers", manufacturers);
            ArrayNode versionList = library.putArray("versions");
            for (JsonNode version : versions.values()) {
                versionList.add(version);
            }
            String libraryJson = mapper.writeValueAsString(library);

            if (fromStart) {
                Files.createDirectories(syncDirectory.toPath());
                AtomicFile.writeString(new File(syncDirectory, LIBRARY_FILE_NAME), libraryJson);

                if (libraryDirectory.exists()) {
                    Files.move(libraryDirectory.toPath(), oldDirectory.toPath());
                }
                Files.move(syncDirectory.toPath(), libraryDirectory.toPath());
                FileUtils.deleteDirectory(oldDirectory);
            } else {
                moveProfiles(syncDirectory, libraryDirectory);
                AtomicFile.writeString(new File(libraryDirectory, LIBRARY_FILE_NAME), libraryJson);
            }

            logger.info("Synced the fixture library ({} pages, {} versions, cursor {})", pages, versions.size(), cursor);
        } finally {
            FileUtils.deleteDirectory(syncDirectory);
        }

        searchLibrary = null;
    }

    private void scheduleSync(Duration delay) {
        try {
            syncScheduler.schedule(this::syncInBackground, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The app is stopping
        }
    }

    private void syncInBackground() {
        Duration nextSync = syncInterval;

        try {
            updateProfiles();
        } catch (Exception e) {
            // E.g. no internet: the library of the last sync stays
            logger.info("Could not sync the fixture library, trying again in {}: {}", syncRetryInterval, e.getMessage());
            nextSync = syncRetryInterval;
        }

        scheduleSync(nextSync);
    }

    @Override
    public void startBackgroundSync() {
        if (backgroundSyncStarted.compareAndSet(false, true)) {
            scheduleSync(firstSyncDelay);
        }
    }

    @PreDestroy
    public void stopBackgroundSync() {
        syncScheduler.shutdownNow();
    }

}
