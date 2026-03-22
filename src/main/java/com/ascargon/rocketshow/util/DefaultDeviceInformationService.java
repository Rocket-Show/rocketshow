package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class DefaultDeviceInformationService implements DeviceInformationService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultDeviceInformationService.class);

    private static final Path CFG_PATH = Path.of("/boot/firmware/device-information.conf");

    private DeviceInformation deviceInformation;

    @Override
    public synchronized DeviceInformation getDeviceInformation() {
        if (deviceInformation != null) {
            return deviceInformation;
        }

        DeviceInformation loadedDeviceInformation = new DeviceInformation();
        deviceInformation = loadedDeviceInformation;

        if (!Files.exists(CFG_PATH)) {
            return deviceInformation;
        }

        deviceInformation.setAvailable(true);

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
            logger.error("Failed to read device information from {}", CFG_PATH, e);
        }

        return deviceInformation;
    }

    private static void append(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(key).append('=').append(value).append('\n');
        }
    }

    @Override
    public synchronized void storeDeviceInformation(DeviceInformation toStore) throws Exception {
        logger.info("Provision device information...");

        if (Files.exists(CFG_PATH)) {
            logger.error("Device information already provisioned");
            throw new IllegalStateException("Device information already provisioned");
        }

        if (toStore.getSerial() == null || toStore.getSerial().isEmpty()) {
            logger.error("Serial is empty");
            throw new IllegalStateException("Serial is empty");
        }

        StringBuilder sb = new StringBuilder();
        append(sb, "FILE_VERSION", toStore.getFileVersion());
        append(sb, "COUNTRY", toStore.getCountry());
        append(sb, "SERIAL", toStore.getSerial());
        append(sb, "MODEL", toStore.getModel());
        append(sb, "HARDWARE_REVISION", toStore.getHardwareRevision());
        append(sb, "SKU", toStore.getSku());

        // Secure write with sync
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(
                CFG_PATH,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            channel.write(ByteBuffer.wrap(bytes));
            channel.force(true); // <-- fsync: flush content + metadata
        }

        // Also sync the parent
        try (FileChannel dirChannel = FileChannel.open(
                CFG_PATH.getParent(),
                StandardOpenOption.READ
        )) {
            dirChannel.force(true);
        }

        // Clear read cache
        deviceInformation = null;

        logger.info("Device information provisioned");
    }

}