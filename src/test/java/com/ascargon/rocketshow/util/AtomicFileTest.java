package com.ascargon.rocketshow.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileTest {

    @TempDir
    Path directory;

    private List<String> files() throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void replacesTheFile() throws Exception {
        Path file = Files.writeString(directory.resolve("settings.xml"), "old");

        AtomicFile.writeString(file.toFile(), "new");

        assertEquals("new", Files.readString(file));
        assertEquals(List.of("settings.xml"), files());
    }

    @Test
    void keepsTheOldFileWhenWritingFails() throws Exception {
        Path file = Files.writeString(directory.resolve("settings.xml"), "old");

        assertThrows(IOException.class, () -> AtomicFile.write(file.toFile(), outputStream -> {
            outputStream.write("half of the n".getBytes());
            throw new IllegalStateException("Power cut");
        }));

        assertEquals("old", Files.readString(file));
        assertEquals(List.of("settings.xml"), files());
    }

}
