package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private void processManufacturerDirectory(String manufacturerShortName, String manufacturerName, List<SearchFixtureTemplate> searchFixtureTemplates) throws IOException {
        File folder;
        File[] fileList;

        logger.debug("Get all fixtures for manufacturer '" + manufacturerShortName + "'");

        folder = new File(basePath + File.separator + manufacturerShortName);
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile() && !fileFilterService.filterFile(file.getName())) {
                    ObjectMapper mapper = new ObjectMapper();
                    FixtureTemplate fixtureTemplate = mapper.readValue(file, FixtureTemplate.class);

                    SearchFixtureTemplate searchFixtureTemplate = new SearchFixtureTemplate();
                    searchFixtureTemplate.setUuid(manufacturerShortName + "/" + file.getName().substring(0, file.getName().length() - 5));
                    searchFixtureTemplate.setName(fixtureTemplate.getName());
                    if (fixtureTemplate.getCategories() != null) {
                        searchFixtureTemplate.setMainCategory(fixtureTemplate.getCategories().get(0));
                    }
                    searchFixtureTemplate.setManufacturerShortName(manufacturerShortName);
                    searchFixtureTemplate.setManufacturerName(manufacturerName);

                    searchFixtureTemplates.add(searchFixtureTemplate);
                }
            }
        }
    }

    private void buildCache() throws IOException {
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

    @Override
    public void invalidateCache() {
        searchFixtureTemplates = null;
    }

    @Override
    public List<SearchFixtureTemplate> searchFixtures(String uuid, String manufacturerShortName, String name, String mainCategory) throws IOException {
        // Return a list of fixtures based on the search criteria

        if (searchFixtureTemplates == null) {
            buildCache();
        }

        if(uuid != null) {
            for(SearchFixtureTemplate searchFixtureTemplate : searchFixtureTemplates) {
                if(uuid.equals(searchFixtureTemplate.getUuid())) {
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

    public void updateLibrary() {
        // Update all fixtures from the internet
        // TODO
    }

}
