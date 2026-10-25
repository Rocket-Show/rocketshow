package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Backups are created and restored with bash and tar
@EnabledOnOs({OS.LINUX, OS.MAC})
class DefaultBackupServiceTest {

    @TempDir
    Path basePath;

    private final RebootService rebootService = mock(RebootService.class);
    private final CountDownLatch rebooted = new CountDownLatch(1);

    private DefaultBackupService backupService;

    @BeforeEach
    void setUp() throws Exception {
        Settings settings = new Settings();
        settings.setBasePath(basePath + File.separator);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);

        doAnswer(invocation -> {
            rebooted.countDown();
            return null;
        }).when(rebootService).reboot();

        backupService = new DefaultBackupService(settingsService, rebootService);
    }

    private void write(String path, String content) throws Exception {
        Path file = basePath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String read(String path) throws Exception {
        return Files.readString(basePath.resolve(path));
    }

    private void upload(byte[] backup) throws Exception {
        // Upload in two chunks like the web app does with larger files
        int half = backup.length / 2;

        backupService.restoreInit();
        backupService.restoreAddChunk(new ByteArrayInputStream(Arrays.copyOfRange(backup, 0, half)));
        backupService.restoreAddChunk(new ByteArrayInputStream(Arrays.copyOfRange(backup, half, backup.length)));
    }

    private void writeData() throws Exception {
        write("compositions/Song.xml", "<composition/>");
        write("sets/Set.xml", "<set/>");
        write("media/audio/song.wav", "audio");
        write("session.xml", "<session/>");
        write("settings.xml", "<settings>original</settings>");
    }

    // Like the download
    private byte[] createBackup() throws Exception {
        ByteArrayOutputStream download = new ByteArrayOutputStream();
        backupService.create(download);
        return download.toByteArray();
    }

    @Test
    void restoresTheCreatedBackup() throws Exception {
        writeData();
        byte[] backup = createBackup();

        // Change the data after the backup
        write("settings.xml", "<settings>changed</settings>");
        Files.delete(basePath.resolve("compositions/Song.xml"));
        write("compositions/New.xml", "<composition/>");

        upload(backup);
        backupService.restoreFinish();

        assertTrue(rebooted.await(30, TimeUnit.SECONDS));
        assertEquals("<settings>original</settings>", read("settings.xml"));
        assertEquals("<composition/>", read("compositions/Song.xml"));
        assertFalse(Files.exists(basePath.resolve("compositions/New.xml")));
        assertEquals("audio", read("media/audio/song.wav"));
        assertFalse(Files.exists(basePath.resolve(".restore")));
    }

    @Test
    void createsTheBackupOfANewDevice() throws Exception {
        // No sets, designer projects, fixtures or media yet
        write("settings.xml", "<settings>original</settings>");
        write("compositions/Song.xml", "<composition/>");
        byte[] backup = createBackup();

        write("settings.xml", "<settings>changed</settings>");

        upload(backup);
        backupService.restoreFinish();

        assertTrue(rebooted.await(30, TimeUnit.SECONDS));
        assertEquals("<settings>original</settings>", read("settings.xml"));
        assertEquals("<composition/>", read("compositions/Song.xml"));
    }

    @Test
    void failsWhenTheBackupCannotBeCreated() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read every file");

        writeData();
        File unreadable = basePath.resolve("compositions/Song.xml").toFile();
        assertTrue(unreadable.setReadable(false, false));

        assertThrows(Exception.class, () -> backupService.create(new ByteArrayOutputStream()));
    }

    @Test
    void keepsTheDataWhenTheBackupIsIncomplete() throws Exception {
        writeData();
        byte[] backup = createBackup();

        // Refused at the latest when finishing the upload
        assertThrows(Exception.class, () -> {
            upload(Arrays.copyOf(backup, backup.length / 2));
            backupService.restoreFinish();
        });
        verify(rebootService, never()).reboot();
        assertEquals("<settings>original</settings>", read("settings.xml"));
        assertEquals("<composition/>", read("compositions/Song.xml"));
    }

    @Test
    void keepsTheDataWhenTheUploadIsNoBackup() throws Exception {
        writeData();

        assertThrows(Exception.class, () -> {
            upload("this is not a backup".getBytes(StandardCharsets.UTF_8));
            backupService.restoreFinish();
        });
        verify(rebootService, never()).reboot();
        assertEquals("<settings>original</settings>", read("settings.xml"));
        assertEquals("<composition/>", read("compositions/Song.xml"));
    }

    @Test
    void keepsTheDataUntilTheWholeBackupIsUploaded() throws Exception {
        writeData();
        byte[] backup = createBackup();
        write("settings.xml", "<settings>changed</settings>");

        upload(backup);

        // Unpacked aside, without a backup file
        assertEquals("<settings>changed</settings>", read("settings.xml"));
        try (Stream<Path> files = Files.list(basePath)) {
            assertEquals(List.of(".restore", "compositions", "media", "session.xml", "sets", "settings.xml"), files.map(file -> file.getFileName().toString()).sorted().toList());
        }

        backupService.restoreFinish();
        assertTrue(rebooted.await(30, TimeUnit.SECONDS));
        assertEquals("<settings>original</settings>", read("settings.xml"));
    }

    @Test
    void restoresAfterACancelledUpload() throws Exception {
        writeData();
        byte[] backup = createBackup();
        write("settings.xml", "<settings>changed</settings>");

        backupService.restoreInit();
        backupService.restoreAddChunk(new ByteArrayInputStream(Arrays.copyOf(backup, backup.length / 2)));

        upload(backup);
        backupService.restoreFinish();

        assertTrue(rebooted.await(30, TimeUnit.SECONDS));
        assertEquals("<settings>original</settings>", read("settings.xml"));
    }

    @Test
    void removesTheRestOfAnInterruptedRestoreAtTheStart() throws Exception {
        write(".restore/media/audio/song.wav", "audio");
        write("backup.tar.gz", "backup of an earlier release");

        setUp();

        assertFalse(Files.exists(basePath.resolve(".restore")));
        assertFalse(Files.exists(basePath.resolve("backup.tar.gz")));
    }

}
