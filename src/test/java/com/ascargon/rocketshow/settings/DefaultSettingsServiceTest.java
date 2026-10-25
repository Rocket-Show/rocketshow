package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.midi.MidiService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import com.ascargon.rocketshow.util.OperatingSystemInformation;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Starts the settings service with the settings of earlier releases
 * (src/test/resources/compat/&lt;version&gt;/settings.xml), like a device does after an update.
 */
class DefaultSettingsServiceTest {

    private final static int CURRENT_SETTINGS_VERSION = 2;

    // Settings of earlier releases, which were removed on purpose
    private final static Set<String> REMOVED_ELEMENTS = Set.of(
            // The global MIDI mapping always applies
            "overrideParent"
    );

    @TempDir
    Path tempPath;

    private final OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
    private final MidiService midiService = mock(MidiService.class);
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);

    @BeforeEach
    void setUp() {
        OperatingSystemInformation operatingSystemInformation = new OperatingSystemInformation();
        operatingSystemInformation.setType(OperatingSystemInformation.Type.LINUX);
        operatingSystemInformation.setSubType(OperatingSystemInformation.SubType.RASPBERRYOS);

        when(operatingSystemInformationService.getOperatingSystemInformation()).thenReturn(operatingSystemInformation);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());
    }

    // Start the service like the app does. It loads, migrates and saves the settings in the directory.
    private DefaultSettingsService start(Path directory) {
        return new DefaultSettingsService(operatingSystemInformationService, midiService, deviceInformationService, mock(ApplicationEventPublisher.class), directory.toString());
    }

    // All values of an XML file by their path, e.g. "settings/audioBusList/audioBus[0]/name" -> "My audio bus 1"
    private Map<String, String> getValues(Path file) throws Exception {
        Map<String, String> values = new HashMap<>();
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        addValues(root, root.getTagName(), values);
        return values;
    }

    private void addValues(Element element, String path, Map<String, String> values) {
        Map<String, Integer> indexByName = new HashMap<>();
        boolean hasChildElements = false;

        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                hasChildElements = true;
                int index = indexByName.merge(child.getTagName(), 1, Integer::sum) - 1;
                addValues(child, path + "/" + child.getTagName() + "[" + index + "]", values);
            }
        }

        if (!hasChildElements) {
            values.put(path, element.getTextContent().trim());
        }
    }

    private List<String> getLostValues(Map<String, String> before, Map<String, String> after) {
        List<String> lostValues = new ArrayList<>();

        for (Map.Entry<String, String> entry : before.entrySet()) {
            String element = entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1, entry.getKey().lastIndexOf('['));
            if (REMOVED_ELEMENTS.contains(element)) {
                continue;
            }

            if (!entry.getValue().equals(after.get(entry.getKey()))) {
                lostValues.add(entry.getKey() + "=" + entry.getValue() + " (now " + after.get(entry.getKey()) + ")");
            }
        }

        return lostValues;
    }

    @TestFactory
    Stream<DynamicTest> startsWithTheSettingsOfEarlierReleases() throws Exception {
        Path compatPath = Path.of(getClass().getResource("/compat").toURI());

        List<Path> settingsFiles;
        try (Stream<Path> paths = Files.list(compatPath)) {
            settingsFiles = paths.map(path -> path.resolve("settings.xml")).filter(Files::exists).sorted().toList();
        }

        assertFalse(settingsFiles.isEmpty());

        return settingsFiles.stream().map(settingsFile -> DynamicTest.dynamicTest(compatPath.relativize(settingsFile).toString(), () -> {
            Path directory = Files.createTempDirectory(tempPath, "settings");
            Path savedFile = directory.resolve("settings.xml");
            Files.copy(settingsFile, savedFile);

            DefaultSettingsService settingsService = start(directory);

            assertEquals(CURRENT_SETTINGS_VERSION, settingsService.getSettings().getVersion());

            // Nothing of the old file is lost when the settings are saved
            List<String> lostValues = getLostValues(getValues(settingsFile), getValues(savedFile));
            assertTrue(lostValues.isEmpty(), compatPath.relativize(settingsFile) + " lost values: " + lostValues);

            // The next start loads the saved settings unchanged
            String saved = Files.readString(savedFile);
            start(directory);
            assertEquals(saved, Files.readString(savedFile));
        }));
    }

    @Test
    void appliesTheDefaultsOfANewDeviceToOldSettings() throws Exception {
        // Settings of an older release don't contain the settings added since then
        Path oldDirectory = Files.createTempDirectory(tempPath, "old");
        Files.writeString(oldDirectory.resolve("settings.xml"), "<settings><version>" + CURRENT_SETTINGS_VERSION + "</version></settings>");
        start(oldDirectory);

        Path newDirectory = Files.createTempDirectory(tempPath, "new");
        start(newDirectory);

        Map<String, String> oldValues = getValues(oldDirectory.resolve("settings.xml"));
        Map<String, String> newValues = getValues(newDirectory.resolve("settings.xml"));

        // Random per device or depending on the directory
        for (Map<String, String> values : List.of(oldValues, newValues)) {
            values.keySet().removeIf(path -> path.endsWith("/uuid[0]") || path.equals("settings/basePath[0]"));
        }

        Set<String> paths = new TreeSet<>(newValues.keySet());
        paths.addAll(oldValues.keySet());

        List<String> differences = new ArrayList<>();
        for (String path : paths) {
            if (!Objects.equals(newValues.get(path), oldValues.get(path))) {
                differences.add(path + ": new device " + newValues.get(path) + ", old settings " + oldValues.get(path));
            }
        }

        assertTrue(differences.isEmpty(), "Old settings don't get the defaults of a new device: " + differences);
    }

}
