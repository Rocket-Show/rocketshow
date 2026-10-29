package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.settings.Settings;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Loads an XML file like the app does and reports every element the current classes don't read, because its content
 * would be lost silently on the next save.
 */
public class StrictXml {

    // Elements that are not read on purpose, per file type
    private final static Map<Class<?>, Set<String>> IGNORED_ELEMENTS = Map.of(
            Composition.class, Set.of(
                    // Only written. The file type follows from the element (audioFile, midiFile, videoFile).
                    "type",
                    // Removed from the MIDI mapping, the global mapping always applies
                    "overrideParent"
            ),
            Settings.class, Set.of(
                    // Removed from the MIDI mapping, the global mapping always applies
                    "overrideParent"
            )
    );

    private static boolean isIgnored(String message, Class<?> type) {
        for (String element : IGNORED_ELEMENTS.getOrDefault(type, Set.of())) {
            if (message.contains("local:\"" + element + "\"")) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getProblems(Path file, Class<?> type) throws Exception {
        List<String> problems = new ArrayList<>();

        Unmarshaller unmarshaller = JAXBContext.newInstance(type).createUnmarshaller();
        unmarshaller.setEventHandler(event -> {
            if (!isIgnored(event.getMessage(), type)) {
                problems.add(event.getMessage());
            }

            // Continue to collect all problems of the file
            return true;
        });

        assertNotNull(unmarshaller.unmarshal(file.toFile()));
        return problems;
    }

}
