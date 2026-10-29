package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.StrictXml;
import com.ascargon.rocketshow.lighting.designer.ActionScene;
import com.ascargon.rocketshow.midi.MidiRouting;
import com.ascargon.rocketshow.midi.MidiService;
import com.ascargon.rocketshow.raspberry.ActionTriggerRaspberryGpio;
import com.ascargon.rocketshow.util.ActionTransport;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starts the settings service with the settings of earlier releases
 * (src/test/resources/compat/&lt;version&gt;/settings.xml), like a device does after an update.
 */
class DefaultSettingsServiceTest {

    // Settings of earlier releases, which were removed on purpose
    private final static Set<String> REMOVED_ELEMENTS = Set.of(
            // The global MIDI mapping always applies
            "overrideParent",
            // Replaced by midiTimecodeMode, which also says whether a timecode is followed
            "midiTimecodeEnabled"
    );

    // Changed by a migration on purpose, and asserted on their own
    private final static Set<String> MIGRATED_ELEMENTS = Set.of(
            "version"
    );

    @TempDir
    Path tempPath;

    private final OperatingSystemInformationService operatingSystemInformationService = mock(OperatingSystemInformationService.class);
    private final MidiService midiService = mock(MidiService.class);
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

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
        return new DefaultSettingsService(operatingSystemInformationService, midiService, deviceInformationService, applicationEventPublisher, directory.toString());
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
            if (REMOVED_ELEMENTS.contains(element) || MIGRATED_ELEMENTS.contains(element)) {
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

            assertEquals(DefaultSettingsService.CURRENT_SETTINGS_VERSION, settingsService.getSettings().getVersion());

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
    void startsANewUnit() throws Exception {
        // A new unit has no settings yet and saves the defaults
        Path directory = Files.createTempDirectory(tempPath, "new");
        Path settingsFile = directory.resolve("settings.xml");
        start(directory);

        List<String> problems = StrictXml.getProblems(settingsFile, Settings.class);
        assertTrue(problems.isEmpty(), "The settings of a new unit are not understood: " + problems);

        // The next start loads the saved settings unchanged
        String saved = Files.readString(settingsFile);
        start(directory);
        assertEquals(saved, Files.readString(settingsFile));
    }

    @Test
    void appliesTheDefaultsOfANewDeviceToOldSettings() throws Exception {
        // Settings of an older release don't contain the settings added since then
        Path oldDirectory = Files.createTempDirectory(tempPath, "old");
        Files.writeString(oldDirectory.resolve("settings.xml"), "<settings><version>" + DefaultSettingsService.CURRENT_SETTINGS_VERSION + "</version></settings>");
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

    @Test
    void publishesChangedMidiRoutings() throws Exception {
        DefaultSettingsService settingsService = start(Files.createTempDirectory(tempPath, "settings"));

        // Nothing changed
        settingsService.save();
        verify(applicationEventPublisher, never()).publishEvent(any(MidiRoutingsChangedEvent.class));

        // Another routing of the MIDI input
        settingsService.getSettings().getDeviceInMidiRoutingList().add(new MidiRouting());
        settingsService.save();
        verify(applicationEventPublisher, times(1)).publishEvent(any(MidiRoutingsChangedEvent.class));

        // Another routing of remote MIDI
        settingsService.getSettings().getRemoteMidiRoutingList().add(new MidiRouting());
        settingsService.save();
        verify(applicationEventPublisher, times(2)).publishEvent(any(MidiRoutingsChangedEvent.class));

        // Another global mapping
        settingsService.getSettings().getMidiMapping().setNoteOffset(12);
        settingsService.save();
        verify(applicationEventPublisher, times(3)).publishEvent(any(MidiRoutingsChangedEvent.class));

        // Other settings don't touch the routings (e.g. while a show is running)
        settingsService.getSettings().setOffsetMillisMidi(10);
        settingsService.save();
        verify(applicationEventPublisher, times(3)).publishEvent(any(MidiRoutingsChangedEvent.class));
    }

    @Test
    void publishesChangedGpio() throws Exception {
        DefaultSettingsService settingsService = start(Files.createTempDirectory(tempPath, "settings"));

        settingsService.save();
        verify(applicationEventPublisher, never()).publishEvent(any(RaspberryGpioChangedEvent.class));

        // Another button
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(22);
        settingsService.getSettings().getActionTriggerRaspberryGpioList().add(trigger);
        settingsService.save();
        verify(applicationEventPublisher, times(1)).publishEvent(any(RaspberryGpioChangedEvent.class));

        // Another action of the button
        ActionTransport play = new ActionTransport();
        play.setTransportActionType(ActionTransport.TransportActionType.PLAY);
        trigger.getActionList().add(play);
        settingsService.save();
        verify(applicationEventPublisher, times(2)).publishEvent(any(RaspberryGpioChangedEvent.class));

        // Another output
        settingsService.getSettings().getRaspberryGpioOutputPinBcmList().add(23);
        settingsService.save();
        verify(applicationEventPublisher, times(3)).publishEvent(any(RaspberryGpioChangedEvent.class));

        settingsService.getSettings().setOffsetMillisMidi(10);
        settingsService.save();
        verify(applicationEventPublisher, times(3)).publishEvent(any(RaspberryGpioChangedEvent.class));
    }


    @Test
    void keepsTheSceneAButtonPlaysAfterARestart() throws Exception {
        Path directory = Files.createTempDirectory(tempPath, "scene");
        DefaultSettingsService settingsService = start(directory);

        ActionScene play = new ActionScene();
        play.setSceneActionType(ActionScene.SceneActionType.PLAY_SCENE);
        play.setProjectName("Club Lights");
        play.setScene("Warm wash");
        play.setUntilStopped(true);
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(22);
        trigger.getActionList().add(play);
        settingsService.getSettings().getActionTriggerRaspberryGpioList().add(trigger);
        settingsService.save();

        ActionScene loaded = (ActionScene) start(directory).getSettings().getActionTriggerRaspberryGpioList().getFirst().getActionList().getFirst();
        assertEquals(ActionScene.SceneActionType.PLAY_SCENE, loaded.getSceneActionType());
        assertEquals("Club Lights", loaded.getProjectName());
        assertEquals("Warm wash", loaded.getScene());
        assertTrue(loaded.isUntilStopped());
    }

    @Test
    void keepsSettingsWhichCannotBeLoaded() throws Exception {
        // E.g. damaged by an earlier release losing its power while saving
        Path directory = Files.createTempDirectory(tempPath, "broken");
        String broken = "<?xml version=\"1.0\"?>\n<settings>\n    <deviceName>Stage left</deviceName>\n    <audioBus";
        Files.writeString(directory.resolve("settings.xml"), broken);

        DefaultSettingsService settingsService = start(directory);

        // The unit starts with the defaults, someone can get the settings back from the copy
        assertEquals(broken, Files.readString(directory.resolve("settings.xml.broken")));
        assertEquals(directory + java.io.File.separator, settingsService.getSettings().getBasePath());
    }

    @Test
    void writesNoHalfSettings() throws Exception {
        Path directory = Files.createTempDirectory(tempPath, "atomic");
        DefaultSettingsService settingsService = start(directory);

        settingsService.getSettings().setDeviceName("Stage right");
        settingsService.save();

        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of("settings.xml"), files.map(path -> path.getFileName().toString()).toList());
        }
        assertEquals("Stage right", start(directory).getSettings().getDeviceName());
    }

}
