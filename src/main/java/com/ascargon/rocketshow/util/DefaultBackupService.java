package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.settings.SettingsService;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages backups. Backups are never stored on the unit: a backup is written straight into the download and a backup
 * being restored is unpacked while it's uploaded, so a unit needs no room for the backup file. The files the backup
 * holds unchanged are linked instead of unpacked again, so restoring over the same data needs room only for what
 * changed.
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

    // The uploaded chunks of the backup being restored, for the thread unpacking them. Only a few wait, so the upload
    // waits for the unpacking instead of filling the memory.
    private BlockingQueue<byte[]> restoreChunks;
    private Thread restoreThread;
    private volatile Throwable restoreError;

    // Smaller files are always unpacked: they take little room and might still change until the restore finishes (e.g.
    // the settings or the session), which would change a linked file as well
    private final static long MIN_LINKED_FILE_SIZE = 1024 * 1024;

    // The end of the upload, after the last chunk
    private final static byte[] END_OF_UPLOAD = new byte[0];

    // The uploaded chunks as one stream, as they arrive
    private static class ChunkInputStream extends InputStream {

        private final BlockingQueue<byte[]> chunks;
        private byte[] chunk = new byte[0];
        private int position;
        private boolean ended;

        private ChunkInputStream(BlockingQueue<byte[]> chunks) {
            this.chunks = chunks;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            while (!ended && position >= chunk.length) {
                try {
                    chunk = chunks.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("The restore was cancelled");
                }

                position = 0;
                ended = chunk == END_OF_UPLOAD;
            }

            if (ended) {
                return -1;
            }

            int count = Math.min(length, chunk.length - position);
            System.arraycopy(chunk, position, buffer, offset, count);
            position += count;
            return count;
        }

    }

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

    private void cancelRestore() throws InterruptedException {
        if (restoreThread != null) {
            restoreThread.interrupt();
            restoreThread.join(10000);
            restoreThread = null;
        }

        restoreChunks = null;
        FileUtils.deleteQuietly(getRestoreDirectory());
    }

    // The file of the current data, which the backup holds unchanged: same place, size and time
    private static boolean isUnchanged(Path currentFile, TarArchiveEntry entry) throws IOException {
        return entry.getSize() >= MIN_LINKED_FILE_SIZE
                && Files.isRegularFile(currentFile, LinkOption.NOFOLLOW_LINKS)
                && Files.size(currentFile) == entry.getSize()
                && Files.getLastModifiedTime(currentFile, LinkOption.NOFOLLOW_LINKS).toInstant().getEpochSecond() == entry.getModTime().toInstant().getEpochSecond();
    }

    private static void setPermissions(Path file, int mode) {
        PosixFilePermission[] bits = {
                PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
                PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ
        };
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);

        for (int i = 0; i < bits.length; i++) {
            if ((mode & (1 << i)) != 0) {
                permissions.add(bits[i]);
            }
        }

        try {
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException | IOException e) {
            logger.debug("Could not set the permissions of {}", file, e);
        }
    }

    // Unpacks the uploaded backup into the restore directory. A file of the current data, which the backup holds
    // unchanged (e.g. a video), is linked instead of unpacked: it takes no room twice.
    private void unpack(InputStream upload, Path restoreDirectory) throws IOException {
        Path basePath = Path.of(settingsService.getSettings().getBasePath()).toAbsolutePath().normalize();

        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(upload)))) {
            TarArchiveEntry entry;

            while ((entry = tar.getNextEntry()) != null) {
                Path target = restoreDirectory.resolve(entry.getName()).normalize();

                if (!target.startsWith(restoreDirectory) || target.equals(restoreDirectory)) {
                    throw new IOException("The backup holds a file outside of its folder: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                if (!entry.isFile()) {
                    logger.warn("Skipping '{}' of the backup, which is no file", entry.getName());
                    continue;
                }

                Files.createDirectories(target.getParent());

                Path currentFile = basePath.resolve(entry.getName()).normalize();
                if (isUnchanged(currentFile, entry)) {
                    try {
                        Files.createLink(target, currentFile);
                        continue;
                    } catch (IOException | UnsupportedOperationException e) {
                        logger.debug("Could not link {}, unpacking it", currentFile, e);
                    }
                }

                Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                Files.setLastModifiedTime(target, FileTime.from(entry.getModTime().toInstant()));
                setPermissions(target, entry.getMode());
            }
        }
    }

    @Override
    public synchronized void restoreInit() throws Exception {
        // E.g. an upload which was cancelled
        cancelRestore();

        Path restoreDirectory = getRestoreDirectory().toPath().toAbsolutePath().normalize();
        Files.createDirectories(restoreDirectory);

        BlockingQueue<byte[]> chunks = new ArrayBlockingQueue<>(2);
        restoreChunks = chunks;
        restoreError = null;
        restoreThread = new Thread(() -> {
            try {
                unpack(new ChunkInputStream(chunks), restoreDirectory);
            } catch (Throwable e) {
                // Whatever went wrong: never let an incomplete restore look complete
                restoreError = e;
            }
        }, "backup-restore");
        restoreThread.setDaemon(true);
        restoreThread.start();
    }

    // Hands a chunk to the unpacking, waiting while it's busy. When it stopped (e.g. on an error), the chunk isn't needed.
    private void handOver(byte[] chunk) throws InterruptedException {
        while (!restoreChunks.offer(chunk, 100, TimeUnit.MILLISECONDS)) {
            if (!restoreThread.isAlive()) {
                return;
            }
        }
    }

    private String getRestoreErrorMessage() {
        return restoreError.getMessage() == null ? restoreError.getClass().getSimpleName() : restoreError.getMessage();
    }

    @Override
    public synchronized void restoreAddChunk(InputStream inputStream) throws Exception {
        if (restoreThread == null) {
            throw new Exception("No backup is being restored");
        }

        handOver(inputStream.readAllBytes());

        // E.g. the upload is no backup or the disk is full
        if (restoreError != null) {
            String message = getRestoreErrorMessage();
            cancelRestore();
            throw new Exception("The backup could not be restored: " + message);
        }
    }

    @Override
    public synchronized void restoreFinish() throws Exception {
        if (restoreThread == null) {
            throw new Exception("No backup is being restored");
        }

        logger.info("Start restoring backup...");

        handOver(END_OF_UPLOAD);
        restoreThread.join();
        restoreThread = null;
        restoreChunks = null;

        // An incomplete upload, no backup or a full disk: keep the data as it is
        if (restoreError != null) {
            String message = getRestoreErrorMessage();
            cancelRestore();
            throw new Exception("The backup file is broken and could not be restored: " + message);
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
