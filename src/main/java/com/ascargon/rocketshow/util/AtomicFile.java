package com.ascargon.rocketshow.util;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file in one step: a unit losing its power while writing keeps either the old or the new file, never a
 * part of it. Units are often switched off at the wall.
 */
public class AtomicFile {

    @FunctionalInterface
    public interface Writer {
        void write(OutputStream outputStream) throws Exception;
    }

    private AtomicFile() {
    }

    public static void write(File file, Writer writer) throws IOException {
        // Hidden, next to the file: the same file system, so it can replace the file at once
        File tempFile = new File(file.getAbsoluteFile().getParentFile(), "." + file.getName() + ".tmp");

        try {
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                writer.write(outputStream);
                outputStream.flush();
                outputStream.getFD().sync();
            }

            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile.toPath());
            throw e;
        } catch (Exception e) {
            Files.deleteIfExists(tempFile.toPath());
            throw new IOException("Could not write " + file, e);
        }
    }

    public static void writeString(File file, String content) throws IOException {
        write(file, outputStream -> outputStream.write(content.getBytes(StandardCharsets.UTF_8)));
    }

    public static void marshal(Marshaller marshaller, Object object, File file) throws JAXBException {
        try {
            write(file, outputStream -> marshaller.marshal(object, outputStream));
        } catch (IOException e) {
            throw new JAXBException("Could not write " + file, e);
        }
    }

}
