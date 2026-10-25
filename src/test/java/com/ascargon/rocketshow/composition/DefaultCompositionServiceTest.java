package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.gstreamer.GstDiscovererService;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.settings.Capabilities;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Storing the compositions and sets of a unit and keeping the sets up to date with the compositions.
 */
class DefaultCompositionServiceTest {

    @TempDir
    Path directory;

    private SettingsService settingsService;
    private final CapabilitiesService capabilitiesService = mock(CapabilitiesService.class);
    private DefaultCompositionService compositionService;

    @BeforeEach
    void setUp() {
        settingsService = TestSettings.inDirectory(directory);
        when(capabilitiesService.getCapabilities()).thenReturn(new Capabilities());
        compositionService = start();
    }

    // Like a restart of the unit: everything is loaded from the files again
    private DefaultCompositionService start() {
        return new DefaultCompositionService(settingsService, capabilitiesService, mock(GstDiscovererService.class));
    }

    private Composition composition(String name, long... actionTriggerPositionsMillis) {
        Composition composition = new Composition();
        composition.setName(name);

        for (long positionMillis : actionTriggerPositionsMillis) {
            composition.getActionTriggerList().add(actionAt(positionMillis));
        }

        return composition;
    }

    private ActionTriggerComposition actionAt(long positionMillis) {
        ActionTriggerComposition actionTrigger = new ActionTriggerComposition();
        actionTrigger.setPositionMillis(positionMillis);
        return actionTrigger;
    }

    private Set set(String name, String... compositionNames) {
        Set set = new Set();
        set.setName(name);

        for (String compositionName : compositionNames) {
            SetComposition setComposition = new SetComposition();
            setComposition.setName(compositionName);
            set.getSetCompositionList().add(setComposition);
        }

        return set;
    }

    private List<String> names(List<Composition> compositions) {
        return compositions.stream().map(Composition::getName).toList();
    }

    private List<String> setEntries(String setName) {
        return compositionService.getSet(setName).getSetCompositionList().stream()
                .map(setComposition -> setComposition.getName() + " " + setComposition.getDurationMillis())
                .toList();
    }

    // A MIDI file with a single note of the passed length at 120 BPM
    private void writeMidiFile(String name, int beats) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480L * beats));

        Path midiDirectory = Files.createDirectories(directory.resolve("media/midi"));
        MidiSystem.write(sequence, 1, midiDirectory.resolve(name).toFile());
    }

    @Test
    void keepsCompositionsAndSetsOverARestart() throws Exception {
        Composition composition = composition("Song 1", 1000, 90000);
        composition.setNotes("Capo 2");
        compositionService.saveComposition(composition);
        compositionService.saveSet(set("Show", "Song 1"));

        DefaultCompositionService restarted = start();

        Composition loaded = restarted.getComposition("Song 1");
        assertEquals("Capo 2", loaded.getNotes());
        assertEquals(90000, loaded.getDurationMillis());
        assertEquals(2, loaded.getActionTriggerList().size());
        assertEquals("Show", restarted.getSet("Show").getName());
        assertEquals("Song 1", restarted.getSet("Show").getSetCompositionList().getFirst().getName());
    }

    @Test
    void ordersTheCompositionsByName() throws Exception {
        for (String name : new String[]{"B", "C", "A"}) {
            compositionService.saveComposition(composition(name));
        }

        assertEquals(List.of("A", "B", "C"), names(compositionService.getAllCompositions()));
        assertEquals(List.of("A", "B", "C"), names(start().getAllCompositions()));

        Composition a = compositionService.getComposition("A");
        Composition c = compositionService.getComposition("C");
        assertEquals("B", compositionService.getNextComposition(a).getName());
        assertNull(compositionService.getNextComposition(c));
        assertEquals("B", compositionService.getPreviousComposition(c).getName());
        assertNull(compositionService.getPreviousComposition(a));
        assertNull(compositionService.getNextComposition(composition("Unknown")));
    }

    @Test
    void takesTheDurationFromTheFilesAndActions() throws Exception {
        // 8 beats at 120 BPM
        writeMidiFile("song.mid", 8);
        Composition composition = composition("Song 1", 1000);
        MidiCompositionFile midiFile = new MidiCompositionFile();
        midiFile.setName("song.mid");
        composition.getCompositionFileList().add(midiFile);

        compositionService.saveComposition(composition);
        assertEquals(4000, compositionService.getComposition("Song 1").getDurationMillis(), 10);

        // An action after the end of the file
        composition.getActionTriggerList().add(actionAt(6000));
        compositionService.saveComposition(composition);
        assertEquals(6000, compositionService.getComposition("Song 1").getDurationMillis());
    }

    @Test
    void needsGstreamerForAudio() {
        Capabilities capabilities = new Capabilities();
        capabilities.setGstreamer(false);
        when(capabilitiesService.getCapabilities()).thenReturn(capabilities);

        Composition composition = composition("Song 1");
        AudioCompositionFile audioFile = new AudioCompositionFile();
        audioFile.setName("song.wav");
        composition.getCompositionFileList().add(audioFile);

        assertThrows(Exception.class, () -> compositionService.saveComposition(composition));
        assertNull(compositionService.getComposition("Song 1"));
    }

    @Test
    void keepsTheDurationsOfTheSetsUpToDate() throws Exception {
        compositionService.saveComposition(composition("Song 1", 60000));
        compositionService.saveComposition(composition("Song 2", 120000));
        compositionService.saveSet(set("Show", "Song 2", "Song 1"));
        assertEquals(List.of("Song 2 120000", "Song 1 60000"), setEntries("Show"));

        compositionService.saveComposition(composition("Song 1", 90000));

        assertEquals(List.of("Song 2 120000", "Song 1 90000"), setEntries("Show"));
        compositionService = start();
        assertEquals(List.of("Song 2 120000", "Song 1 90000"), setEntries("Show"));
    }

    @Test
    void removesDeletedCompositionsFromTheSets() throws Exception {
        compositionService.saveComposition(composition("Song 1"));
        compositionService.saveComposition(composition("Song 2"));
        compositionService.saveSet(set("Show", "Song 1", "Song 2"));

        compositionService.deleteComposition("Song 1");

        assertNull(compositionService.getComposition("Song 1"));
        assertEquals(List.of("Song 2 0"), setEntries("Show"));
        assertEquals(List.of("Song 2"), names(start().getAllCompositions()));
    }

    @Test
    void deletesSets() throws Exception {
        compositionService.saveSet(set("Show"));
        compositionService.saveSet(set("Rehearsal"));

        compositionService.deleteSet("Show");

        assertNull(compositionService.getSet("Show"));
        assertEquals(List.of("Rehearsal"), start().getAllSets().stream().map(Set::getName).toList());
    }


    @Test
    void skipsHiddenAndBrokenFiles() throws Exception {
        compositionService.saveComposition(composition("Song 1"));
        compositionService.saveSet(set("Show", "Song 1"));

        // Left by a power cut while writing, copied from a Mac, damaged
        Files.writeString(directory.resolve("compositions/.Song 2.xml.tmp"), "<composition>");
        Files.writeString(directory.resolve("compositions/._Song 1.xml"), "binary");
        Files.writeString(directory.resolve("compositions/Song 3.xml"), "<composition><name>Song 3");
        Files.writeString(directory.resolve("sets/.Show.xml.tmp"), "<set>");

        DefaultCompositionService restarted = start();

        assertEquals(List.of("Song 1"), names(restarted.getAllCompositions()));
        assertEquals(List.of("Show"), restarted.getAllSets().stream().map(Set::getName).toList());
    }

}
