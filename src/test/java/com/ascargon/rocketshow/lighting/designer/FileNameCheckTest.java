package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.DeviceRegistrationService;
import com.ascargon.rocketshow.online.OnlineApiClient;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Names of designer projects and fixtures must not reach files outside their folder.
 */
class FileNameCheckTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = TestSettings.inDirectory(directory);
    }

    private DefaultDesignerService designerService() {
        return new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    private DefaultFixtureService fixtureService() {
        return new DefaultFixtureService(settingsService, mock(FileFilterService.class), mock(DeviceRegistrationService.class),
                mock(AccountService.class), new OnlineApiClient("http://127.0.0.1:1/designer/api/"));
    }

    @Test
    void savesAndDeletesProjects() throws Exception {
        DefaultDesignerService designerService = designerService();

        designerService.saveProject("{\"name\":\"Show 1\"}");
        Path file = directory.resolve("designer/Show 1.json");
        assertTrue(Files.exists(file));

        designerService.deleteProjectByName("Show 1");
        assertFalse(Files.exists(file));
    }

    @Test
    void keepsProjectsInTheDesignerFolder() throws Exception {
        DefaultDesignerService designerService = designerService();
        Path file = Files.writeString(directory.resolve("other.json"), "{}");

        assertThrows(IllegalArgumentException.class, () -> designerService.deleteProjectByName("../other"));
        assertThrows(IllegalArgumentException.class, () -> designerService.saveProject("{\"name\":\"../other\"}"));

        assertEquals("{}", Files.readString(file));
    }

    @Test
    void readsFixturesOfAManufacturer() throws Exception {
        Path fixture = Files.createDirectories(directory.resolve("fixtures/acme")).resolve("par.json");
        Files.writeString(fixture, "{\"name\":\"Par\"}");

        assertEquals("{\"name\":\"Par\"}", fixtureService().getFixture("acme/par"));
    }

    @Test
    void keepsFixturesInTheFixtureFolder() throws Exception {
        DefaultFixtureService fixtureService = fixtureService();
        Files.createDirectories(directory.resolve("fixtures"));
        Files.writeString(directory.resolve("other.json"), "{}");

        for (String uuid : new String[]{"../other", "acme/../../other", "/other", "other", "acme/"}) {
            assertThrows(IllegalArgumentException.class, () -> fixtureService.getFixture(uuid), uuid);
        }
    }

    @Test
    void readsVersionsOfAProfile() throws Exception {
        // Named by the uuid of the version, "<manufacturer>/<fixture>~<version>"
        Path fixture = Files.createDirectories(directory.resolve("fixtures/acme")).resolve("par~3.json");
        Files.writeString(fixture, "{\"name\":\"Par\"}");

        assertEquals("{\"name\":\"Par\"}", fixtureService().getFixture("acme/par~3"));
    }

}
