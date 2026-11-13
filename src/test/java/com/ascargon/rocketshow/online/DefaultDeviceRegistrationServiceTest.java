package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Fixture;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.BuildInfo;
import com.ascargon.rocketshow.util.BuildInfoService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a device tells the online designer about itself with each sync of the fixture library.
 */
class DefaultDeviceRegistrationServiceTest {

    @TempDir
    Path directory;

    private Settings settings;
    private DeviceInformationService deviceInformationService;
    private AccountService accountService;
    private VersionService versionService;
    private BuildInfoService buildInfoService;
    private CompositionService compositionService;
    private DesignerService designerService;
    private DefaultDeviceRegistrationService registrationService;

    @BeforeEach
    void setUp() throws Exception {
        settings = new Settings();
        settings.setLanguage("de");
        settings.setDeviceName("Spark of the band");
        settings.setUpdateTestBranch(false);
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        DeviceIdentifierService deviceIdentifierService = mock(DeviceIdentifierService.class);
        when(deviceIdentifierService.getIdentifier()).thenReturn("hashed-identifier");

        deviceInformationService = mock(DeviceInformationService.class);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());

        accountService = mock(AccountService.class);
        when(accountService.getStatus()).thenReturn(new AccountStatus(false, null));

        VersionInfo versionInfo = new VersionInfo();
        versionInfo.setVersion("3.2.0");
        versionService = mock(VersionService.class);
        when(versionService.getCurrentVersionInfo()).thenReturn(versionInfo);

        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setBuildNumber("20261113120000");
        buildInfoService = mock(BuildInfoService.class);
        when(buildInfoService.getBuildInfo()).thenReturn(buildInfo);

        compositionService = mock(CompositionService.class);
        when(compositionService.getAllCompositions()).thenReturn(List.of(new Composition(), new Composition(), new Composition()));
        when(compositionService.getAllSets()).thenReturn(List.of(new Set()));

        designerService = mock(DesignerService.class);
        when(designerService.getAllProjects()).thenReturn(List.of(
                project("stairville/mh-100~12", "cameo/flat-par", "stairville/mh-100~12"),
                project("cameo/flat-par", null),
                project()
        ));

        registrationService = new DefaultDeviceRegistrationService(settingsService, deviceIdentifierService, accountService,
                deviceInformationService, versionService, buildInfoService, compositionService, designerService);

        // Like a Raspberry Pi
        registrationService.piModelFile = Files.writeString(directory.resolve("model"), "Raspberry Pi 4 Model B Rev 1.4\0");
        registrationService.osReleaseFile = Files.writeString(directory.resolve("os-release"),
                "PRETTY_NAME=\"Debian GNU/Linux 12 (bookworm)\"\nNAME=\"Debian GNU/Linux\"\nID=debian\n");
    }

    private static Project project(String... profileUuids) {
        List<Fixture> fixtures = new ArrayList<>();

        for (String profileUuid : profileUuids) {
            Fixture fixture = new Fixture();
            fixture.setProfileUuid(profileUuid);
            fixtures.add(fixture);
        }

        Project project = new Project();
        project.setFixtures(fixtures);
        return project;
    }

    @Test
    void tellsWhatTheCommunityEditionIsAndHowItIsUsed() throws Exception {
        JsonNode device = registrationService.getRegistration();

        assertEquals("rocketshow", device.get("kind").asString());
        assertEquals("hashed-identifier", device.get("identifier").asString());
        assertEquals("community", device.get("edition").asString());
        assertFalse(device.has("model"));
        assertFalse(device.has("sku"));
        assertEquals("Raspberry Pi 4 Model B Rev 1.4", device.get("piModel").asString());
        assertEquals(System.getProperty("os.arch"), device.get("architecture").asString());
        assertEquals("Debian GNU/Linux 12 (bookworm)", device.get("os").asString());
        assertEquals("3.2.0", device.get("version").asString());
        assertEquals("20261113120000", device.get("build").asString());
        assertEquals("stable", device.get("updateBranch").asString());
        assertEquals("de", device.get("language").asString());
        assertEquals(3, device.get("compositions").asInt());
        assertEquals(1, device.get("sets").asInt());
        assertEquals(3, device.get("projects").asInt());
        assertEquals(5, device.get("fixtures").asInt());

        // Each profile once, in no order of the projects
        List<String> profiles = new ArrayList<>();
        device.get("profiles").forEach(profile -> profiles.add(profile.asString()));
        assertEquals(List.of("cameo/flat-par", "stairville/mh-100~12"), profiles);

        // Only while linked
        assertFalse(device.has("name"));
    }

    @Test
    void tellsTheHardwareOfASpark() throws Exception {
        DeviceInformation spark = new DeviceInformation();
        spark.setAvailable(true);
        spark.setSerial("RS-SPARK-00042");
        spark.setModel("Spark");
        spark.setSku("RS-SPARK-1");
        spark.setHardwareRevision("B");
        when(deviceInformationService.getDeviceInformation()).thenReturn(spark);

        JsonNode device = registrationService.getRegistration();

        assertEquals("spark", device.get("edition").asString());
        assertEquals("Spark", device.get("model").asString());
        assertEquals("RS-SPARK-1", device.get("sku").asString());
        assertEquals("B", device.get("hardwareRevision").asString());

        // Never the serial itself
        assertFalse(device.toString().contains("RS-SPARK-00042"));
    }

    @Test
    void tellsTheNameOfTheDeviceWhileLinked() throws Exception {
        when(accountService.getStatus()).thenReturn(new AccountStatus(true, "band@example.com"));

        assertEquals("Spark of the band", registrationService.getRegistration().get("name").asString());
    }

    @Test
    void tellsTheTestBranch() throws Exception {
        settings.setUpdateTestBranch(true);

        assertEquals("test", registrationService.getRegistration().get("updateBranch").asString());
    }

    @Test
    void registersWithoutVersionOrRaspberryPi() throws Exception {
        // E.g. started from the IDE on a laptop
        when(versionService.getCurrentVersionInfo()).thenReturn(new VersionInfo());
        when(buildInfoService.getBuildInfo()).thenReturn(new BuildInfo());
        registrationService.piModelFile = directory.resolve("missing");
        registrationService.osReleaseFile = directory.resolve("missing");

        JsonNode device = registrationService.getRegistration();

        assertFalse(device.has("version"));
        assertFalse(device.has("build"));
        assertFalse(device.has("piModel"));
        assertEquals(System.getProperty("os.name") + " " + System.getProperty("os.version"), device.get("os").asString());
    }

}
