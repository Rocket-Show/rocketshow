package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.RocketShowApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class DefaultDeviceInformationService implements DeviceInformationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDeviceInformationService.class);

    private static final Path CFG_PATH = Path.of("/provision/device-information.conf");

    private DeviceInformation deviceInformation;

    @Override
    public synchronized DeviceInformation getDeviceInformation() {
        if (deviceInformation != null) {
            return deviceInformation;
        }

        DeviceInformation loadedDeviceInformation = new DeviceInformation();

        if (!Files.exists(CFG_PATH)) {
            // Not provisioned (yet). Don't cache, so the file is picked up once it appears
            // (e.g. the /provision partition is mounted or the device is provisioned later).
            logger.debug("No device information available");
            return loadedDeviceInformation;
        }

        loadedDeviceInformation.setAvailable(true);
        logger.debug("Device information is available");

        try {
            List<String> lines = Files.readAllLines(CFG_PATH, StandardCharsets.UTF_8);

            for (String line : lines) {
                if (line == null) {
                    continue;
                }

                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int idx = line.indexOf('=');
                if (idx < 0) {
                    continue;
                }

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();

                switch (key) {
                    case "FILE_VERSION" -> loadedDeviceInformation.setFileVersion(value);
                    case "COUNTRY" -> loadedDeviceInformation.setCountry(value);
                    case "SERIAL" -> loadedDeviceInformation.setSerial(value);
                    case "MODEL" -> loadedDeviceInformation.setModel(value);
                    case "HARDWARE_REVISION" -> loadedDeviceInformation.setHardwareRevision(value);
                    case "SKU" -> loadedDeviceInformation.setSku(value);
                    default -> {
                        // ignore unknown keys
                    }
                }
            }
        } catch (IOException e) {
            // Don't cache a failed read, so it can be retried on the next call.
            logger.error("Failed to read device information from {}", CFG_PATH, e);
            return loadedDeviceInformation;
        }

        // Only cache a successful read, so a transient failure or a not-yet-ready
        // /provision partition can never poison the cache with empty data.
        deviceInformation = loadedDeviceInformation;
        return deviceInformation;
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(key).append('=').append(value).append('\n');
        }
    }

    @Override
    public synchronized void storeDeviceInformation(DeviceInformation toStore) throws Exception {
        DeviceInformation currentDeviceInformation = getDeviceInformation();
        if (currentDeviceInformation.isAvailable()
                && currentDeviceInformation.getSerial() != null
                && !currentDeviceInformation.getSerial().isEmpty()
        ) {
            throw new IllegalStateException("Device information is already provisioned");
        }

        logger.info("Provision device information...");

        if (toStore.getSerial() == null || toStore.getSerial().isEmpty()) {
            throw new IllegalStateException("Serial is empty");
        }

        StringBuilder sb = new StringBuilder();
        append(sb, "FILE_VERSION", toStore.getFileVersion());
        append(sb, "COUNTRY", toStore.getCountry());
        append(sb, "SERIAL", toStore.getSerial());
        append(sb, "MODEL", toStore.getModel());
        append(sb, "HARDWARE_REVISION", toStore.getHardwareRevision());
        append(sb, "SKU", toStore.getSku());

        ProcessBuilder pb = new ProcessBuilder(
                "sudo",
                new ApplicationHome(RocketShowApplication.class).getDir() + File.separator + "store-device-info.sh"
        );

        Process p = pb.start();

        try (OutputStream os = p.getOutputStream()) {
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int rc = p.waitFor();
        if (rc != 0) {
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("Root write failed: " + err);
        }

        deviceInformation = null;
        logger.info("Device information provisioned");
    }

}