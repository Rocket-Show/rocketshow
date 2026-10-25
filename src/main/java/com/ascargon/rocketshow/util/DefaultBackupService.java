package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.settings.SettingsService;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages backups. Backups are never stored on the unit: a backup is written straight into the download and a backup
 * being restored is unpacked while it's uploaded, so a unit needs no room for the backup file.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultBackupService implements BackupService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultBackupService.class);

    private final List<String> backedUpFileList = Arrays.asList("compositions", "sets", "designer", "fixtures", "media", "session.xml", "settings.xml");

    private final SettingsService settingsService;
    private final RebootService rebootService;

    // Releases before stored the backup file here
    private final static String BACKUP_FILE_NAME = "backup.tar.gz";

    // The backup being restored is unpacked here, until it's complete
    private final static String RESTORE_DIRECTORY_NAME = ".restore";

    // tar unpacking the backup being uploaded
    private Process restoreProcess;
    private ByteArrayOutputStream restoreErrors;
    private Thread restoreErrorReader;

    public DefaultBackupService(SettingsService settingsService, RebootService rebootService) {
        this.settingsService = settingsService;
        this.rebootService = rebootService;

        // The rest of an interrupted restore or a backup file of an earlier release
        FileUtils.deleteQuietly(getRestoreDirectory());
        FileUtils.deleteQuietly(new File(settingsService.getSettings().getBasePath(), BACKUP_FILE_NAME));
    }

    private File getRestoreDirectory() {
        return new File(settingsService.getSettings().getBasePath(), RESTORE_DIRECTORY_NAME);
    }

    // Reads what a command writes as errors, so it can't block on a full buffer
    private static Thread readErrors(Process process, ByteArrayOutputStream errors) {
        Thread thread = new Thread(() -> {
            try {
                process.getErrorStream().transferTo(errors);
            } catch (IOException e) {
                // The command ended
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    public void create(OutputStream outputStream) throws Exception {
        // Only back up what exists (e.g. there might be no fixtures yet), because tar fails on missing files
        String basePath = settingsService.getSettings().getBasePath();
        List<String> command = new ArrayList<>(List.of("tar", "-czpf", "-"));
        command.addAll(backedUpFileList.stream()
                .filter(file -> new File(basePath, file).exists())
                .toList());

        // tar all backed up files
        // don't use zip, because the file permissions (e.g. execution rights) are not preserved easily with java
        Process process = new ProcessBuilder(command).directory(new File(basePath)).start();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread errorReader = readErrors(process, errors);

        try (InputStream backup = process.getInputStream()) {
            backup.transferTo(outputStream);
        } catch (IOException e) {
            // E.g. the download was cancelled
            process.destroy();
            throw e;
        }

        int exitCode = process.waitFor();
        errorReader.join();

        // Never let an incomplete backup look complete (e.g. a file could not be read): the download breaks off
        if (exitCode != 0) {
            throw new Exception("Could not create the backup: " + errors.toString(StandardCharsets.UTF_8).trim());
        }
    }

    private void cancelRestore() {
        if (restoreProcess != null) {
            restoreProcess.destroyForcibly();
            restoreProcess = null;
        }

        FileUtils.deleteQuietly(getRestoreDirectory());
    }

    @Override
    public synchronized void restoreInit() throws Exception {
        // E.g. an upload which was cancelled
        cancelRestore();

        File restoreDirectory = getRestoreDirectory();
        Files.createDirectories(restoreDirectory.toPath());

        restoreProcess = new ProcessBuilder("tar", "-xzpf", "-", "-C", restoreDirectory.getPath())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        restoreErrors = new ByteArrayOutputStream();
        restoreErrorReader = readErrors(restoreProcess, restoreErrors);
    }

    @Override
    public synchronized void restoreAddChunk(InputStream inputStream) throws Exception {
        if (restoreProcess == null) {
            throw new Exception("No backup is being restored");
        }

        try {
            inputStream.transferTo(restoreProcess.getOutputStream());
            restoreProcess.getOutputStream().flush();
        } catch (IOException e) {
            // tar stopped, e.g. the upload is no backup or the disk is full
            restoreProcess.waitFor();
            restoreErrorReader.join();
            String errors = restoreErrors.toString(StandardCharsets.UTF_8).trim();
            cancelRestore();
            throw new Exception("The backup could not be restored: " + errors);
        }
    }

    @Override
    public synchronized void restoreFinish() throws Exception {
        if (restoreProcess == null) {
            throw new Exception("No backup is being restored");
        }

        logger.info("Start restoring backup...");

        restoreProcess.getOutputStream().close();
        int exitCode = restoreProcess.waitFor();
        restoreErrorReader.join();
        restoreProcess = null;

        // An incomplete upload, no backup or a full disk: keep the data as it is
        if (exitCode != 0) {
            String errors = restoreErrors.toString(StandardCharsets.UTF_8).trim();
            cancelRestore();
            throw new Exception("The backup file is broken and could not be restored: " + errors);
        }

        // Replace the backed up files with the ones of the backup. The backup is on the same disk, so they are only
        // moved.
        Path basePath = Path.of(settingsService.getSettings().getBasePath());
        Path restoreDirectory = getRestoreDirectory().toPath();

        for (String file : backedUpFileList) {
            FileUtils.deleteQuietly(basePath.resolve(file).toFile());

            if (Files.exists(restoreDirectory.resolve(file))) {
                Files.move(restoreDirectory.resolve(file), basePath.resolve(file));
            }
        }

        FileUtils.deleteQuietly(restoreDirectory.toFile());

        logger.info("Backup has been restored. Reboot...");

        // Answer the request before
        Thread thread = new Thread(() -> {
            try {
                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not reboot after restoring the backup", e);
            }
        });
        thread.start();
    }

}
