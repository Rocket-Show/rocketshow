package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.DefaultChunkedFileUploadService;
import com.ascargon.rocketshow.util.DefaultFileFilterService;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Uploading media files and lead sheets in chunks, like the web app does.
 */
class MediaUploadTest {

    @TempDir
    Path directory;

    private DefaultCompositionFileService compositionFileService;
    private DefaultLeadSheetService leadSheetService;

    @BeforeEach
    void setUp() {
        SettingsService settingsService = TestSettings.inDirectory(directory);
        DefaultChunkedFileUploadService chunkedFileUploadService = new DefaultChunkedFileUploadService();

        compositionFileService = new DefaultCompositionFileService(settingsService, new DefaultFileFilterService(), chunkedFileUploadService);
        leadSheetService = new DefaultLeadSheetService(settingsService, chunkedFileUploadService);
    }

    private static ByteArrayInputStream chunk(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private Path audio(String name) {
        return directory.resolve("media/audio").resolve(name);
    }

    private List<String> files() {
        return compositionFileService.getAllFiles().stream().map(file -> file.getClass().getSimpleName() + " " + file.getName()).sorted().toList();
    }

    @Test
    void putsTheChunksTogether() throws Exception {
        compositionFileService.saveFileInit("song.wav");
        compositionFileService.saveFileAddChunk(chunk("first "), "song.wav");
        compositionFileService.saveFileAddChunk(chunk("second "), "song.wav");
        compositionFileService.saveFileAddChunk(chunk("third"), "song.wav");
        CompositionFile compositionFile = compositionFileService.saveFileFinish("song.wav");

        assertInstanceOf(AudioCompositionFile.class, compositionFile);
        assertEquals("song.wav", compositionFile.getName());
        assertEquals("first second third", Files.readString(audio("song.wav")));
        assertEquals(List.of("AudioCompositionFile song.wav"), files());
    }

    @Test
    void keepsTheOldFileUntilTheNewOneIsComplete() throws Exception {
        Files.createDirectories(audio("song.wav").getParent());
        Files.writeString(audio("song.wav"), "old version");

        compositionFileService.saveFileInit("song.wav");
        compositionFileService.saveFileAddChunk(chunk("new "), "song.wav");

        // E.g. played during the upload
        assertEquals("old version", Files.readString(audio("song.wav")));
        assertEquals(List.of("AudioCompositionFile song.wav"), files());

        compositionFileService.saveFileAddChunk(chunk("version"), "song.wav");
        compositionFileService.saveFileFinish("song.wav");

        assertEquals("new version", Files.readString(audio("song.wav")));
    }

    @Test
    void leavesNoHalfFileAfterAnInterruptedUpload() throws Exception {
        compositionFileService.saveFileInit("song.wav");
        compositionFileService.saveFileAddChunk(chunk("first "), "song.wav");

        // The connection dropped, nothing appears
        assertEquals(List.of(), files());

        // The next upload of the same file starts over
        compositionFileService.saveFileInit("song.wav");
        compositionFileService.saveFileAddChunk(chunk("complete"), "song.wav");
        compositionFileService.saveFileFinish("song.wav");

        assertEquals("complete", Files.readString(audio("song.wav")));
    }

    @Test
    void sortsTheFilesByTheirType() throws Exception {
        for (String name : new String[]{"click.WAV", "backing.mp3", "song.mid", "intro.mp4"}) {
            compositionFileService.saveFileInit(name);
            compositionFileService.saveFileAddChunk(chunk(name), name);
            compositionFileService.saveFileFinish(name);
        }
        Files.writeString(directory.resolve("media/audio/.DS_Store"), "");

        assertEquals(List.of("AudioCompositionFile backing.mp3", "AudioCompositionFile click.WAV", "MidiCompositionFile song.mid", "VideoCompositionFile intro.mp4"), files());
        assertInstanceOf(MidiCompositionFile.class, compositionFileService.getAllFiles().stream().filter(file -> file.getName().equals("song.mid")).findFirst().orElseThrow());
        assertInstanceOf(VideoCompositionFile.class, compositionFileService.getAllFiles().stream().filter(file -> file.getName().equals("intro.mp4")).findFirst().orElseThrow());
    }

    @Test
    void refusesFilesItCannotPlay() {
        assertThrows(Exception.class, () -> compositionFileService.saveFileInit("notes.txt"));
    }

    @Test
    void uploadsLeadSheetsTheSameWay() throws Exception {
        leadSheetService.saveLeadSheetInit("chords.png");
        leadSheetService.saveLeadSheetAddChunk(chunk("half"), "chords.png");
        assertEquals(List.of(), leadSheetService.getAllLeadSheets().stream().map(LeadSheet::getName).toList());

        leadSheetService.saveLeadSheetAddChunk(chunk(" and the rest"), "chords.png");
        leadSheetService.saveLeadSheetFinish("chords.png");

        assertEquals(List.of("chords.png"), leadSheetService.getAllLeadSheets().stream().map(LeadSheet::getName).toList());
        assertEquals("half and the rest", Files.readString(leadSheetService.getImage("chords.png").toPath()));
    }

}
