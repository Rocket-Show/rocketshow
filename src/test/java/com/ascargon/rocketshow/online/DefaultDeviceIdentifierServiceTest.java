package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The identifier a device registers with: the serial of a Spark, else a random id kept in the base path, always
 * hashed.
 */
class DefaultDeviceIdentifierServiceTest {

    @TempDir
    Path basePath;

    private SettingsService settingsService;
    private DeviceInformationService deviceInformationService;

    @BeforeEach
    void setUp() {
        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);
        settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        deviceInformationService = mock(DeviceInformationService.class);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());
    }

    private DefaultDeviceIdentifierService identifierService() {
        return new DefaultDeviceIdentifierService(settingsService, deviceInformationService);
    }

    @Test
    void hashesTheSerialOfASpark() throws Exception {
        DeviceInformation spark = new DeviceInformation();
        spark.setAvailable(true);
        spark.setSerial("RS-SPARK-00042");
        when(deviceInformationService.getDeviceInformation()).thenReturn(spark);

        // sha256 of "rocketshow-device:RS-SPARK-00042", as the API of the online designer finds a Spark by its serial
        assertEquals("c19ee1633ec46a63cdf405fccbdeadf72315979c7eb04d00ab9eff6f3e43cc6e", identifierService().getIdentifier());
        assertFalse(Files.exists(basePath.resolve(DefaultDeviceIdentifierService.FILE_NAME)));
    }

    @Test
    void keepsARandomIdWithoutASerial() throws Exception {
        String identifier = identifierService().getIdentifier();

        // Made once and kept, also for the next start
        Path file = basePath.resolve(DefaultDeviceIdentifierService.FILE_NAME);
        assertTrue(Files.isRegularFile(file));
        String id = Files.readString(file).trim();
        assertEquals(DefaultDeviceIdentifierService.hash(id), identifier);
        assertEquals(identifier, identifierService().getIdentifier());
        assertEquals(id, Files.readString(file).trim());

        // Hashed, nothing of the id is sent
        assertTrue(identifier.matches("[0-9a-f]{64}"));
        assertFalse(identifier.contains(id));
    }

    @Test
    void makesAnotherIdOnAnotherDevice() throws Exception {
        String identifier = identifierService().getIdentifier();
        Files.delete(basePath.resolve(DefaultDeviceIdentifierService.FILE_NAME));

        // E.g. a community installation set up anew
        assertNotEquals(identifier, identifierService().getIdentifier());
    }

    @Test
    void takesTheSerialOnceTheSparkIsProvisioned() throws Exception {
        String beforeProvisioning = identifierService().getIdentifier();

        DeviceInformation spark = new DeviceInformation();
        spark.setAvailable(true);
        spark.setSerial("RS-SPARK-00042");
        when(deviceInformationService.getDeviceInformation()).thenReturn(spark);

        assertNotEquals(beforeProvisioning, identifierService().getIdentifier());
        assertEquals(DefaultDeviceIdentifierService.hash("RS-SPARK-00042"), identifierService().getIdentifier());
    }

}
