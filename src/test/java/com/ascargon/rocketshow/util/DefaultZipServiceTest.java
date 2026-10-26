package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.settings.TestSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Zipping the logs for the download in the web app.
 */
class DefaultZipServiceTest {

    @TempDir
    Path directory;

    private final DefaultZipService zipService = new DefaultZipService();

    // The entries of the zip with the content of the files
    private static Map<String, String> entries(Path zip) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : new ArrayList<>(zipFile.stream().toList())) {
                entries.put(entry.getName(), entry.isDirectory() ? "" : new String(zipFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void zipsTheLogsForTheDownload() throws Exception {
        Path log = Files.createDirectories(directory.resolve("log"));
        Files.writeString(log.resolve("rocketshow.log"), "Started RocketShowApplication");
        Files.writeString(log.resolve("rocketshow-2026-10-24.log"), "Played Song 1");
        Files.createDirectories(log.resolve("gstreamer"));
        Files.writeString(log.resolve("gstreamer/pipeline.log"), "Pipeline started");
        Files.writeString(log.resolve(".DS_Store"), "");

        Path logs = new DefaultLogDownloadService(TestSettings.inDirectory(directory), zipService).getLogsFile().toPath();

        assertEquals(Map.of(
                "log/", "",
                "log/rocketshow.log", "Started RocketShowApplication",
                "log/rocketshow-2026-10-24.log", "Played Song 1",
                "log/gstreamer/", "",
                "log/gstreamer/pipeline.log", "Pipeline started"
        ), entries(logs));
    }

    @Test
    void leavesOutIgnoredFiles() throws Exception {
        Path data = Files.createDirectories(directory.resolve("data"));
        Files.writeString(data.resolve("settings.xml"), "<settings/>");
        Files.writeString(data.resolve("logs.zip"), "old download");

        Path zip = directory.resolve("data.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zipService.zipFile(data.toFile(), "data", zipOutputStream, List.of("logs.zip"));
        }

        assertEquals(Map.of("data/", "", "data/settings.xml", "<settings/>"), entries(zip));
    }

    @Test
    void unzipsWhatItZipped() throws Exception {
        Path data = Files.createDirectories(directory.resolve("data/compositions"));
        Files.writeString(data.resolve("Song 1.xml"), "<composition/>");
        Path zip = directory.resolve("data.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zipService.zipFile(directory.resolve("data").toFile(), "data", zipOutputStream, null);
        }

        Path target = Files.createDirectories(directory.resolve("restored"));
        zipService.unzipFile(zip.toString(), target.toFile());

        assertEquals("<composition/>", Files.readString(target.resolve("data/compositions/Song 1.xml")));
    }

    @Test
    void refusesToUnzipOutsideTheTarget() throws Exception {
        Path zip = directory.resolve("evil.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zipOutputStream.putNextEntry(new ZipEntry("../settings.xml"));
            zipOutputStream.write("<settings><password>x</password></settings>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        Path target = Files.createDirectories(directory.resolve("restored"));

        assertThrows(IOException.class, () -> zipService.unzipFile(zip.toString(), target.toFile()));
        assertFalse(Files.exists(directory.resolve("settings.xml")));
    }

}
