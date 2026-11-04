package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.settings.Settings;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
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
                    "overrideParent",
                    // Removed from the video settings, the size of the screen had no effect
                    "videoWidth",
                    "videoHeight",
                    "customVideoResolution"
            )
    );

    // Passes the file on to JAXB and remembers the element JAXB is starting to read, because it reports an element it
    // does not know right then. Not taken from the message, which is in the language of the JVM (e.g. 'lokal:"type"'
    // instead of 'local:"type"' in German).
    private static class StartedElementFilter extends XMLFilterImpl {

        private String startedElement;

        private StartedElementFilter() throws Exception {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            setParent(parserFactory.newSAXParser().getXMLReader());
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            startedElement = localName;

            try {
                super.startElement(uri, localName, qName, attributes);
            } finally {
                startedElement = null;
            }
        }
    }

    public static List<String> getProblems(Path file, Class<?> type) throws Exception {
        List<String> problems = new ArrayList<>();
        Set<String> ignoredElements = IGNORED_ELEMENTS.getOrDefault(type, Set.of());
        StartedElementFilter filter = new StartedElementFilter();

        Unmarshaller unmarshaller = JAXBContext.newInstance(type).createUnmarshaller();
        unmarshaller.setEventHandler(event -> {
            if (filter.startedElement == null || !ignoredElements.contains(filter.startedElement)) {
                problems.add(event.getMessage());
            }

            // Continue to collect all problems of the file
            return true;
        });

        assertNotNull(unmarshaller.unmarshal(new SAXSource(filter, new InputSource(file.toUri().toString()))));
        return problems;
    }

}
