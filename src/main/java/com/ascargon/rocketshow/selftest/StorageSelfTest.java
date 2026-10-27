package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Writes, reads back and deletes a file in the data directory, e.g. to find a read-only or full SD card.
 */
@Service
class StorageSelfTest implements SelfTestStep {

    static final String STORAGE = "storage";

    private static final int SIZE = 1024 * 1024;

    private final SettingsService settingsService;

    StorageSelfTest(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(STORAGE);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        Path directory = Path.of(settingsService.getSettings().getBasePath());
        Path file = directory.resolve(".selftest-" + UUID.randomUUID());

        byte[] content = new byte[SIZE];
        new Random().nextBytes(content);

        long start = System.currentTimeMillis();

        try {
            // Onto the storage, not only into the cache of the system
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            byte[] readBack = Files.readAllBytes(file);

            if (Arrays.equals(content, readBack)) {
                context.report(SelfTestCheck.passed(STORAGE, directory.toString(), "1 MB written and read back in " + (System.currentTimeMillis() - start) + " ms"));
            } else {
                context.report(SelfTestCheck.failed(STORAGE, directory.toString(), "1 MB written, but other content read back"));
            }
        } catch (Exception e) {
            context.report(SelfTestCheck.failed(STORAGE, directory.toString(), "Could not write and read back 1 MB: " + e));
        } finally {
            Files.deleteIfExists(file);
        }
    }

}
