package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.gstreamer.GstDiscovererService;
import com.ascargon.rocketshow.play.PlayerService;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.ChunkedFileUploadService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Names of media files, lead sheets, compositions and sets come from the web app. They must not reach files
 * outside their folder, e.g. the settings.
 */
class FileNameCheckTest {

    @TempDir
    Path directory;

    private Path settingsFile;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = TestSettings.inDirectory(directory);
        settingsFile = directory.resolve("settings.xml");
        assertTrue(Files.exists(settingsFile));
    }

    private void assertRejected(Path file, Executable executable) {
        assertThrows(IllegalArgumentException.class, executable);
        assertTrue(Files.exists(file));
    }

    private DefaultCompositionFileService compositionFileService() {
        return new DefaultCompositionFileService(settingsService, mock(FileFilterService.class), mock(ChunkedFileUploadService.class));
    }

    private DefaultLeadSheetService leadSheetService() {
        return new DefaultLeadSheetService(settingsService, mock(ChunkedFileUploadService.class));
    }

    private DefaultCompositionService compositionService() {
        return new DefaultCompositionService(settingsService, mock(CapabilitiesService.class), mock(GstDiscovererService.class));
    }

    private PlayerService playerService() {
        PlayerService playerService = mock(PlayerService.class);
        when(playerService.getCompositionName()).thenReturn("");
        return playerService;
    }

    @Test
    void deletesMediaFiles() throws Exception {
        Path song = Files.createDirectories(directory.resolve("media/audio")).resolve("song.wav");
        Files.writeString(song, "");

        compositionFileService().deleteFile("song.wav", "AUDIO");

        assertFalse(Files.exists(song));
    }

    @Test
    void keepsMediaFilesInTheMediaFolder() throws Exception {
        DefaultCompositionFileService compositionFileService = compositionFileService();
        Path wav = Files.writeString(directory.resolve("other.wav"), "");

        assertRejected(settingsFile, () -> compositionFileService.deleteFile("../../settings.xml", "AUDIO"));
        assertRejected(settingsFile, () -> compositionFileService.getFile("../../settings.xml", "AUDIO"));
        assertRejected(wav, () -> compositionFileService.saveFileInit("../../other.wav"));
        assertRejected(wav, () -> compositionFileService.saveFileAddChunk(new ByteArrayInputStream(new byte[0]), "../../other.wav"));
    }

    @Test
    void keepsLeadSheetsInTheLeadSheetFolder() throws Exception {
        DefaultLeadSheetService leadSheetService = leadSheetService();

        assertRejected(settingsFile, () -> leadSheetService.deleteLeadSheet("../../settings.xml"));
        assertRejected(settingsFile, () -> leadSheetService.getImage("../../settings.xml"));
        assertRejected(settingsFile, () -> leadSheetService.saveLeadSheetInit("../../settings.xml"));
    }

    @Test
    void savesAndDeletesCompositions() throws Exception {
        DefaultCompositionService compositionService = compositionService();
        Composition composition = new Composition();
        composition.setName("Song 1");

        compositionService.saveComposition(composition);
        Path file = directory.resolve("compositions/Song 1.xml");
        assertTrue(Files.exists(file));

        compositionService.deleteComposition("Song 1", playerService());
        assertFalse(Files.exists(file));
    }

    @Test
    void keepsCompositionsAndSetsInTheirFolders() throws Exception {
        DefaultCompositionService compositionService = compositionService();
        Composition composition = new Composition();
        composition.setName("../settings");
        Set set = new Set();
        set.setName("../settings");

        assertRejected(settingsFile, () -> compositionService.deleteComposition("../settings", playerService()));
        assertRejected(settingsFile, () -> compositionService.deleteSet("../settings"));
        assertRejected(settingsFile, () -> compositionService.saveComposition(composition));
        assertRejected(settingsFile, () -> compositionService.saveSet(set, false));
    }

}
