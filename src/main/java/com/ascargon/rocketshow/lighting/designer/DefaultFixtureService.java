package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import com.ascargon.rocketshow.util.FileNames;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
public class DefaultFixtureService implements FixtureService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultFixtureService.class);

    private final SettingsService settingsService;
    private final FileFilterService fileFilterService;

    private final String MANUFACTURERS_FILE_NAME = "manufacturers.json";

    // How often the library is updated at the start: it changes rarely, and each update rewrites about 600 files on the
    // SD card and holds up the start. The web app updates it on demand.
    private final static Duration UPDATE_INTERVAL = Duration.ofDays(7);

    private List<SearchFixtureTemplate> searchFixtureTemplates;

    private String basePath = "";

    // Package visible for the tests
    String libraryUrl = "https://www.rocketshow.net/designer/downloads/fixtures.zip";

    public DefaultFixtureService(
            SettingsService settingsService,
            FileFilterService fileFilterService
    ) {
        this.settingsService = settingsService;
        this.fileFilterService = fileFilterService;

        basePath = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getFixturePath();
    }

    private void processManufacturerDirectory(String manufacturerShortName, String manufacturerName, List<SearchFixtureTemplate> searchFixtureTemplates) throws Exception {
        File folder;
        File[] fileList;

        logger.debug("Get all fixtures for manufacturer '" + manufacturerShortName + "'");

        folder = new File(basePath + File.separator + manufacturerShortName);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    logger.debug("Load fixture " + file.getAbsolutePath());

                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        FixtureProfile fixtureProfile = mapper.readValue(file, FixtureProfile.class);

                        SearchFixtureTemplate searchFixtureTemplate = new SearchFixtureTemplate();
                        searchFixtureTemplate.setUuid(manufacturerShortName + "/" + file.getName().substring(0, file.getName().length() - 5));
                        searchFixtureTemplate.setName(fixtureProfile.getName());
                        searchFixtureTemplate.setManufacturerShortName(manufacturerShortName);
                        searchFixtureTemplate.setManufacturerName(manufacturerName);

                        searchFixtureTemplates.add(searchFixtureTemplate);
                    } catch (Exception e) {
                        // A profile, which can't be read, must not hide the others from the search
                        logger.error("Could not parse fixture {}", file.getPath(), e);
                    }
                }
            }
        }
    }

    private void buildCache() throws Exception {
        List<SearchFixtureTemplate> searchFixtureTemplates = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        Manufacturers manufacturers = mapper.readValue(new File(basePath + File.separator + MANUFACTURERS_FILE_NAME), Manufacturers.class);

        if (manufacturers.getManufacturers() != null) {
            logger.debug("Found " + manufacturers.getManufacturers().size() + " manufacturers");

            for (Map.Entry<String, Manufacturer> entry : manufacturers.getManufacturers().entrySet()) {
                processManufacturerDirectory(entry.getKey(), entry.getValue().getName(), searchFixtureTemplates);
            }
        }

        this.searchFixtureTemplates = searchFixtureTemplates;
    }

    public void invalidateCache() {
        searchFixtureTemplates = null;
    }

    @Override
    public List<SearchFixtureTemplate> searchFixtures(String uuid, String manufacturerShortName, String name, String mainCategory) throws Exception {
        // Return a list of fixtures based on the search criteria

        if (searchFixtureTemplates == null) {
            buildCache();
        }

        if (uuid != null) {
            for (SearchFixtureTemplate searchFixtureTemplate : searchFixtureTemplates) {
                if (uuid.equals(searchFixtureTemplate.getUuid())) {
                    List<SearchFixtureTemplate> filteredSearchFixtureTemplas = new ArrayList<>();
                    filteredSearchFixtureTemplas.add(searchFixtureTemplate);
                    return filteredSearchFixtureTemplas;
                }
            }

            return null;
        } else {
            // TODO implement the remaining search criteria

            return searchFixtureTemplates;
        }
    }

    // The uuid is "<manufacturer>/<fixture>" and must not leave the fixture directory
    private String checkUuid(String uuid) {
        String[] parts = uuid == null ? new String[0] : uuid.split("/", -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid fixture uuid '" + uuid + "'");
        }

        FileNames.check(parts[0]);
        FileNames.check(parts[1]);

        return uuid;
    }

    @Override
    public String getFixture(String uuid) throws IOException {
        // Return a single fixture based on the uuid
        File file = new File(basePath + File.separator + checkUuid(uuid) + ".json");
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    // Package visible for the tests
    void extractFolder(String zipFile) throws ZipException, IOException {
        try (ZipFile zip = new ZipFile(new File(zipFile))) {
            extractFolder(zip, zipFile);
        }
    }

    private void extractFolder(ZipFile zip, String zipFile) throws IOException {
        int BUFFER = 2048;
        String newPath = zipFile.substring(0, zipFile.length() - 4);

        new File(newPath).mkdir();
        Enumeration zipFileEntries = zip.entries();

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
            String currentEntry = entry.getName();
            File destFile = new File(newPath, currentEntry);

            // Don't write outside the target directory (e.g. an entry named "../x")
            if (!destFile.getCanonicalPath().startsWith(new File(newPath).getCanonicalPath() + File.separator)) {
                throw new IOException("Entry is outside of the target dir: " + currentEntry);
            }
            File destinationParent = destFile.getParentFile();

            // create the parent directory structure if needed
            destinationParent.mkdirs();

            if (!entry.isDirectory()) {
                BufferedInputStream inputStream = new BufferedInputStream(zip
                        .getInputStream(entry));
                int currentByte;
                // establish buffer for writing file
                byte data[] = new byte[BUFFER];

                // write the current file to disk
                FileOutputStream fos = new FileOutputStream(destFile);
                BufferedOutputStream dest = new BufferedOutputStream(fos,
                        BUFFER);

                // read and write until last byte is encountered
                while ((currentByte = inputStream.read(data, 0, BUFFER)) != -1) {
                    dest.write(data, 0, currentByte);
                }
                dest.flush();
                dest.close();
                inputStream.close();
            }

            if (currentEntry.endsWith(".zip")) {
                // found a zip file, try to open
                extractFolder(destFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void updateProfilesIfOutdated() throws IOException {
        File manufacturersFile = new File(new File(basePath).getAbsoluteFile(), MANUFACTURERS_FILE_NAME);

        if (manufacturersFile.isFile() && System.currentTimeMillis() - manufacturersFile.lastModified() < UPDATE_INTERVAL.toMillis()) {
            logger.info("The fixture library is up to date");
            return;
        }

        updateProfiles();
    }

    @Override
    public void updateProfiles() throws IOException {
        // Download the current profile set, if an internet connection is available. The library is replaced, once
        // the new one is complete: a download breaking off (e.g. on a stage network) must not leave the unit without
        // a library. The download is hidden next to the library.
        File libraryDirectory = new File(basePath).getAbsoluteFile();
        File parentDirectory = libraryDirectory.getParentFile();
        File downloadFile = new File(parentDirectory, "." + libraryDirectory.getName() + "-download.zip");
        File downloadDirectory = new File(parentDirectory, "." + libraryDirectory.getName() + "-download");
        File oldDirectory = new File(parentDirectory, "." + libraryDirectory.getName() + "-old");

        // Switched off while replacing the library the last time
        if (!libraryDirectory.exists() && oldDirectory.exists()) {
            Files.move(oldDirectory.toPath(), libraryDirectory.toPath());
        }

        try {
            // The app updates the library at the start, maybe without internet
            URLConnection connection = new URL(libraryUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, downloadFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            FileUtils.deleteDirectory(downloadDirectory);
            extractFolder(downloadFile.getPath());

            if (!new File(downloadDirectory, MANUFACTURERS_FILE_NAME).isFile()) {
                throw new IOException("The downloaded fixture library has no " + MANUFACTURERS_FILE_NAME);
            }

            FileUtils.deleteDirectory(oldDirectory);
            if (libraryDirectory.exists()) {
                Files.move(libraryDirectory.toPath(), oldDirectory.toPath());
            }
            Files.move(downloadDirectory.toPath(), libraryDirectory.toPath());
            FileUtils.deleteDirectory(oldDirectory);

            // The time of the update, for the next start
            new File(libraryDirectory, MANUFACTURERS_FILE_NAME).setLastModified(System.currentTimeMillis());
        } finally {
            Files.deleteIfExists(downloadFile.toPath());
            FileUtils.deleteDirectory(downloadDirectory);
        }

        invalidateCache();
    }

}
