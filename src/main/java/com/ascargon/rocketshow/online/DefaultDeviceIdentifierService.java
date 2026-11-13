package com.ascargon.rocketshow.online;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.AtomicFile;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The identifier of the device: the serial of a Spark, else a random id the device makes once and keeps in its base
 * path. Never the serial of the Raspberry Pi or the machine id of the system: the salt of the hash is in this open
 * source code, so their hashes could be turned back by trying all of them, while a random id says nothing about the
 * hardware. A community installation set up anew is a new device.
 */
@Service
public class DefaultDeviceIdentifierService implements DeviceIdentifierService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDeviceIdentifierService.class);

    // Put in front of the serial or the random id before hashing, as the API of the online designer expects it
    private final static String SALT = "rocketshow-device:";

    // The random id of a device without a serial, in the base path. Not in the backups (see DefaultBackupService): a
    // backup restored on another device must not make it this one.
    public final static String FILE_NAME = "device-identifier";

    private final SettingsService settingsService;
    private final DeviceInformationService deviceInformationService;

    public DefaultDeviceIdentifierService(SettingsService settingsService, DeviceInformationService deviceInformationService) {
        this.settingsService = settingsService;
        this.deviceInformationService = deviceInformationService;
    }

    @Override
    public synchronized String getIdentifier() throws IOException {
        DeviceInformation deviceInformation = deviceInformationService.getDeviceInformation();

        if (deviceInformation != null && deviceInformation.isAvailable() && deviceInformation.getSerial() != null && !deviceInformation.getSerial().isBlank()) {
            return hash(deviceInformation.getSerial());
        }

        return hash(getRandomId());
    }

    private String getRandomId() throws IOException {
        File file = new File(settingsService.getSettings().getBasePath(), FILE_NAME);

        if (file.isFile()) {
            String id = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();

            if (!id.isEmpty()) {
                return id;
            }
        }

        String id = UUID.randomUUID().toString();
        AtomicFile.writeString(file, id);
        logger.info("Created the identifier of the device");

        return id;
    }

    // Package visible for the tests
    static String hash(String id) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((SALT + id).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every Java has SHA-256
            throw new IllegalStateException(e);
        }
    }

}
