package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.settings.SettingsService;
import com.sun.nio.file.ExtendedOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes a file into the data directory, reads it back and deletes it, e.g. to find a read-only, full or slow SD
 * card, and looks for errors of the storage in the kernel log.
 */
@Service
class StorageSelfTest implements SelfTestStep {

    private final static Logger logger = LoggerFactory.getLogger(StorageSelfTest.class);

    static final String STORAGE = "storage";

    private static final int CHUNK = 1024 * 1024;
    static final int CHUNKS = 16;

    // A video of a show reads a few MB a second
    static final double MIN_WRITE_MB_PER_SECOND = 2;
    static final double MIN_READ_MB_PER_SECOND = 10;

    // Errors of the SD card, a USB drive or their file systems
    private static final Pattern STORAGE_ERROR_PATTERN = Pattern.compile("(?i)(\\bmmc\\d|\\bmmcblk\\d|\\bnvme\\d|\\bsd[a-z]\\b|\\bsd[a-z]\\d|ext4-fs|f2fs|i/o error)");

    private final SettingsService settingsService;
    private final SelfTestSystem system;

    StorageSelfTest(SettingsService settingsService, SelfTestSystem system) {
        this.settingsService = settingsService;
        this.system = system;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(STORAGE);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        Path directory = Path.of(settingsService.getSettings().getBasePath());
        Path file = directory.resolve(".selftest-" + UUID.randomUUID());
        long seed = System.nanoTime();

        String speeds;

        try {
            long start = System.nanoTime();
            write(file, seed);
            double writeSpeed = getMbPerSecond(start);

            start = System.nanoTime();
            boolean fromStorage = readBack(file, seed);
            double readSpeed = getMbPerSecond(start);

            speeds = CHUNKS + " MB written at " + format(writeSpeed) + " MB/s and read back "
                    + (fromStorage ? "at " + format(readSpeed) + " MB/s" : "from the cache of the system");

            if (writeSpeed < MIN_WRITE_MB_PER_SECOND || (fromStorage && readSpeed < MIN_READ_MB_PER_SECOND)) {
                context.report(SelfTestCheck.failed(STORAGE, directory.toString(), speeds + ", slower than " + format(MIN_WRITE_MB_PER_SECOND)
                        + " MB/s writing or " + format(MIN_READ_MB_PER_SECOND) + " MB/s reading"));
                return;
            }
        } catch (ReadBackException e) {
            context.report(SelfTestCheck.failed(STORAGE, directory.toString(), e.getMessage()));
            return;
        } catch (Exception e) {
            context.report(SelfTestCheck.failed(STORAGE, directory.toString(), "Could not write and read back " + CHUNKS + " MB: " + e));
            return;
        } finally {
            Files.deleteIfExists(file);
        }

        List<String> errors = getStorageErrors();

        if (errors == null) {
            context.report(SelfTestCheck.passed(STORAGE, directory.toString(), speeds + ", the kernel log can't be read"));
        } else if (errors.isEmpty()) {
            context.report(SelfTestCheck.passed(STORAGE, directory.toString(), speeds + ", no errors in the kernel log"));
        } else {
            context.report(SelfTestCheck.failed(STORAGE, directory.toString(), errors.size() + " errors of the storage in the kernel log, e.g. " + errors.getFirst()));
        }
    }

    private static class ReadBackException extends Exception {

        ReadBackException(String message) {
            super(message);
        }

    }

    private static double getMbPerSecond(long startNanos) {
        return CHUNKS / Math.max((System.nanoTime() - startNanos) / 1e9, 1e-6);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // Onto the storage, not only into the cache of the system
    private static void write(Path file, long seed) throws Exception {
        Random random = new Random(seed);
        byte[] chunk = new byte[CHUNK];

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (int i = 0; i < CHUNKS; i++) {
                random.nextBytes(chunk);
                ByteBuffer buffer = ByteBuffer.wrap(chunk);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
            channel.force(true);
        }
    }

    // From the storage, past the cache of the system, where the file system allows it (false otherwise)
    private static boolean readBack(Path file, long seed) throws Exception {
        try {
            int blockSize = (int) Files.getFileStore(file).getBlockSize();
            ByteBuffer buffer = ByteBuffer.allocateDirect(CHUNK + blockSize).alignedSlice(blockSize).limit(CHUNK);

            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.DIRECT)) {
                compare(channel, buffer, seed);
            }

            return true;
        } catch (ReadBackException e) {
            throw e;
        } catch (Exception e) {
            // E.g. tmpfs
            logger.debug("Could not read past the cache of the system", e);
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            compare(channel, ByteBuffer.allocate(CHUNK), seed);
        }

        return false;
    }

    private static void compare(FileChannel channel, ByteBuffer buffer, long seed) throws Exception {
        Random random = new Random(seed);
        byte[] expected = new byte[CHUNK];
        byte[] read = new byte[CHUNK];

        for (int i = 0; i < CHUNKS; i++) {
            buffer.clear().limit(CHUNK);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    throw new ReadBackException("Read back " + i + " of " + CHUNKS + " MB only");
                }
            }

            buffer.flip();
            buffer.get(read);
            random.nextBytes(expected);

            if (!Arrays.equals(expected, read)) {
                throw new ReadBackException(CHUNKS + " MB written, but other content read back at MB " + (i + 1));
            }
        }
    }

    // The errors of the storage in the kernel log, null if it can't be read
    private List<String> getStorageErrors() {
        for (String[] command : List.of(
                new String[]{"dmesg", "--level=emerg,alert,crit,err"},
                new String[]{"journalctl", "--dmesg", "--boot", "--priority=err", "--no-pager", "--quiet", "--output=cat"})) {
            try {
                SelfTestSystem.CommandResult result = system.run(command);
                if (result.exitCode() == 0) {
                    return getStorageErrors(result.output());
                }
            } catch (Exception e) {
                logger.debug("Could not read the kernel log with {}", command[0], e);
            }
        }

        return null;
    }

    // Package visible for the tests
    static List<String> getStorageErrors(String kernelLog) {
        return kernelLog.lines().filter(line -> STORAGE_ERROR_PATTERN.matcher(line).find()).map(String::strip).toList();
    }

}
