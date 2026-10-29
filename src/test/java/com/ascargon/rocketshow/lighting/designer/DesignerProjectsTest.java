package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.DefaultFileFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Loading the designer projects of a unit.
 */
class DesignerProjectsTest {

    @TempDir
    Path directory;

    @Test
    void loadsTheOtherProjectsBesideABrokenOne() throws Exception {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        Path designer = Files.createDirectories(directory.resolve("designer"));
        Files.writeString(designer.resolve("Show.json"), "{\"name\": \"Show\", \"version\": 7}");
        // Damaged, left while writing, copied from a Mac
        Files.writeString(designer.resolve("Broken.json"), "{\"name\": \"Broken\", \"scenes\": [");
        Files.writeString(designer.resolve(".Show.json.tmp"), "{");
        Files.writeString(designer.resolve("._Show.json"), "binary");

        // The app starts with the designer service
        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());

        assertEquals(List.of("Show"), designerService.getAllProjects().stream().map(Project::getName).toList());
    }

    @Test
    void writesNoHalfProjects() throws Exception {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        DefaultDesignerService designerService = new DefaultDesignerService(settingsService, new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());

        designerService.saveProject("{\"name\": \"Show\", \"version\": 7}");

        try (var files = Files.list(directory.resolve("designer"))) {
            assertEquals(List.of("Show.json"), files.map(path -> path.getFileName().toString()).toList());
        }
    }

}
