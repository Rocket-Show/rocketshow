package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Fixture;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.BuildInfoService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The registration of the device, sent with each sync of the fixture library (see the plan of the fixture library in
 * the designer, section 7). The privacy policy of rocketshow.net says what is sent and why.
 */
@Service
public class DefaultDeviceRegistrationService implements DeviceRegistrationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDeviceRegistrationService.class);

    private final SettingsService settingsService;
    private final DeviceIdentifierService deviceIdentifierService;
    private final AccountService accountService;
    private final DeviceInformationService deviceInformationService;
    private final VersionService versionService;
    private final BuildInfoService buildInfoService;
    private final CompositionService compositionService;
    private final DesignerService designerService;

    private final ObjectMapper mapper = new ObjectMapper();

    // Where the system tells the model of the Raspberry Pi and its name. Package visible for the tests.
    Path piModelFile = Path.of("/sys/firmware/devicetree/base/model");
    Path osReleaseFile = Path.of("/etc/os-release");

    public DefaultDeviceRegistrationService(
            SettingsService settingsService,
            DeviceIdentifierService deviceIdentifierService,
            AccountService accountService,
            DeviceInformationService deviceInformationService,
            VersionService versionService,
            BuildInfoService buildInfoService,
            CompositionService compositionService,
            DesignerService designerService
    ) {
        this.settingsService = settingsService;
        this.deviceIdentifierService = deviceIdentifierService;
        this.accountService = accountService;
        this.deviceInformationService = deviceInformationService;
        this.versionService = versionService;
        this.buildInfoService = buildInfoService;
        this.compositionService = compositionService;
        this.designerService = designerService;
    }

    private static void putIfKnown(ObjectNode node, String name, String value) {
        if (value != null && !value.isBlank()) {
            node.put(name, value.trim());
        }
    }

    // E.g. "Raspberry Pi 4 Model B Rev 1.4", null on other computers
    private String readPiModel() {
        try {
            return Files.isRegularFile(piModelFile) ? Files.readString(piModelFile, StandardCharsets.UTF_8).replace("\0", "").trim() : null;
        } catch (IOException e) {
            logger.debug("Could not read the model of the Raspberry Pi", e);
            return null;
        }
    }

    // E.g. "Debian GNU/Linux 12 (bookworm)"
    private String readOperatingSystem() {
        try {
            if (Files.isRegularFile(osReleaseFile)) {
                for (String line : Files.readAllLines(osReleaseFile, StandardCharsets.UTF_8)) {
                    if (line.startsWith("PRETTY_NAME=")) {
                        return line.substring("PRETTY_NAME=".length()).replace("\"", "").trim();
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Could not read the name of the operating system", e);
        }

        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    private String getVersion() {
        try {
            VersionInfo versionInfo = versionService.getCurrentVersionInfo();
            return versionInfo == null ? null : versionInfo.getVersion();
        } catch (Exception e) {
            logger.debug("Could not read the version", e);
            return null;
        }
    }

    @Override
    public ObjectNode getRegistration() throws IOException {
        Settings settings = settingsService.getSettings();
        ObjectNode device = mapper.createObjectNode();

        device.put("kind", "rocketshow");
        device.put("identifier", deviceIdentifierService.getIdentifier());

        DeviceInformation deviceInformation = deviceInformationService.getDeviceInformation();
        boolean spark = deviceInformation != null && deviceInformation.isAvailable() && deviceInformation.getSerial() != null && !deviceInformation.getSerial().isBlank();
        device.put("edition", spark ? "spark" : "community");

        if (spark) {
            putIfKnown(device, "model", deviceInformation.getModel());
            putIfKnown(device, "sku", deviceInformation.getSku());
            putIfKnown(device, "hardwareRevision", deviceInformation.getHardwareRevision());
        }

        putIfKnown(device, "piModel", readPiModel());
        putIfKnown(device, "architecture", System.getProperty("os.arch"));
        putIfKnown(device, "os", readOperatingSystem());
        putIfKnown(device, "version", getVersion());
        putIfKnown(device, "build", buildInfoService.getBuildInfo().getBuildNumber());
        device.put("updateBranch", Boolean.TRUE.equals(settings.getUpdateTestBranch()) ? "test" : "stable");
        putIfKnown(device, "language", settings.getLanguage());

        device.put("compositions", compositionService.getAllCompositions().size());
        device.put("sets", compositionService.getAllSets().size());

        // A copy: a project may be saved meanwhile
        List<Project> projects = new ArrayList<>(designerService.getAllProjects());
        int fixtureCount = 0;
        TreeSet<String> profileUuids = new TreeSet<>();

        for (Project project : projects) {
            if (project.getFixtures() == null) {
                continue;
            }

            for (Fixture fixture : project.getFixtures()) {
                fixtureCount++;

                if (fixture.getProfileUuid() != null && !fixture.getProfileUuid().isBlank()) {
                    profileUuids.add(fixture.getProfileUuid());
                }
            }
        }

        device.put("projects", projects.size());
        device.put("fixtures", fixtureCount);

        // The versions of the profiles in use, for their usage (a plain uuid counts for its fixture)
        ArrayNode profiles = device.putArray("profiles");
        for (String profileUuid : profileUuids) {
            profiles.add(profileUuid);
        }

        // Only for "My devices" of the account
        if (accountService.getStatus().linked()) {
            putIfKnown(device, "name", settings.getDeviceName());
        }

        return device;
    }

}
