package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private List<SearchFixtureTemplate> searchFixtureTemplates;

    private String basePath = "";

    public DefaultFixtureService(SettingsService settingsService, FileFilterService fileFilterService) {
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
                        throw new Exception("Could not parse fixture " + file.getPath(), e);
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

    @Override
    public String getFixture(String uuid) throws IOException {
        // Return a single fixture based on the uuid
        File file = new File(basePath + File.separator + uuid + ".json");
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    private void extractFolder(String zipFile) throws ZipException, IOException {
        int BUFFER = 2048;
        File file = new File(zipFile);

        ZipFile zip = new ZipFile(file);
        String newPath = zipFile.substring(0, zipFile.length() - 4);

        new File(newPath).mkdir();
        Enumeration zipFileEntries = zip.entries();

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
            String currentEntry = entry.getName();
            File destFile = new File(newPath, currentEntry);
            //destFile = new File(newPath, destFile.getName());
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

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    @Override
    public void updateProfiles() throws IOException {
        String fixturesPath = settingsService.getSettings().getBasePath() + "fixtures";
        File fixturesDirectory = new File(fixturesPath);

        // Download the current profile set, if an internet connection is available
        String downloadFile = settingsService.getSettings().getBasePath() + "fixtures.zip";

        // Download the fixtures ZIP
        URL url = new URL("https://www.rocketshow.net/designer/downloads/fixtures.zip");
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        FileOutputStream fileOutputStream = new FileOutputStream(downloadFile);
        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        fileOutputStream.close();

        // Delete the local directory
        FileUtils.deleteDirectory(fixturesDirectory);

        // Create the directory
        createDirectoryIfNotExists(fixturesPath);

        // Unzip the archive
        extractFolder(downloadFile);

        // Delete the ZIP file
        File systemFile = new File(downloadFile);

        if (!systemFile.exists()) {
            return;
        }

        boolean result = systemFile.delete();

        if (!result) {
            logger.error("Could not delete fixtures ZIP file '" + downloadFile + "'");
        }

        invalidateCache();
    }

}
