package com.ascargon.rocketshow.video;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DefaultHdmiService implements HdmiService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultHdmiService.class);

    // The HDMI connectors of the graphics cards, e.g. card1-HDMI-A-1 and card1-HDMI-A-2 on a Raspberry Pi 4 or 5
    private static final String HDMI_CONNECTOR_PATTERN = "card\\d+-HDMI-A-\\d+";

    private final Path drmPath;

    @Autowired
    public DefaultHdmiService() {
        this(Path.of("/sys/class/drm"));
    }

    // Package visible for the tests
    DefaultHdmiService(Path drmPath) {
        this.drmPath = drmPath;
    }

    private List<Path> getConnectors() {
        try (Stream<Path> paths = Files.list(drmPath)) {
            return paths.filter(path -> path.getFileName().toString().matches(HDMI_CONNECTOR_PATTERN)).sorted().toList();
        } catch (Exception e) {
            logger.debug("Could not list the HDMI connectors in {}", drmPath, e);
            return List.of();
        }
    }

    private static boolean isConnected(Path connector) {
        try {
            return "connected".equalsIgnoreCase(Files.readString(connector.resolve("status")).trim());
        } catch (Exception e) {
            logger.debug("Could not read the HDMI status of {}", connector, e);
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        List<Path> connectors = getConnectors();

        if (connectors.isEmpty()) {
            // Assume hdmi is connected by default, to output video if possible
            logger.debug("No HDMI connectors found in {}", drmPath);
            return true;
        }

        return connectors.stream().anyMatch(DefaultHdmiService::isConnected);
    }

    @Override
    public boolean isDisplayConnected() {
        return getConnectors().stream().anyMatch(DefaultHdmiService::isConnected);
    }

    @Override
    public List<ConnectedDisplay> getConnectedDisplays() {
        List<ConnectedDisplay> connectedDisplays = new ArrayList<>();

        for (Path connector : getConnectors()) {
            if (!isConnected(connector)) {
                continue;
            }

            String mode = null;
            try {
                // The preferred mode of the display comes first
                mode = Files.readAllLines(connector.resolve("modes")).stream().findFirst().orElse(null);
            } catch (Exception e) {
                logger.debug("Could not read the modes of {}", connector, e);
            }

            String name = null;
            try {
                name = getEdidName(Files.readAllBytes(connector.resolve("edid")));
            } catch (Exception e) {
                logger.debug("Could not read the EDID of {}", connector, e);
            }

            String connectorName = connector.getFileName().toString().replaceFirst("^card\\d+-", "");
            connectedDisplays.add(new ConnectedDisplay(connectorName, mode, name));
        }

        return connectedDisplays;
    }

    // The name of the display in its EDID (the descriptor 0xFC of the base block), or null. Package visible for the
    // tests.
    static String getEdidName(byte[] edid) {
        if (edid.length < 128) {
            return null;
        }

        for (int descriptor = 54; descriptor <= 108; descriptor += 18) {
            if (edid[descriptor] == 0 && edid[descriptor + 1] == 0 && edid[descriptor + 2] == 0 && (edid[descriptor + 3] & 0xFF) == 0xFC) {
                String name = new String(edid, descriptor + 5, 13, StandardCharsets.US_ASCII);
                int end = name.indexOf('\n');
                name = (end < 0 ? name : name.substring(0, end)).trim();
                return name.isEmpty() ? null : name;
            }
        }

        return null;
    }

}
