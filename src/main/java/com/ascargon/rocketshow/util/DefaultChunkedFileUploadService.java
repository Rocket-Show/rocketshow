package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.*;
import java.io.*;

@Service
public class DefaultChunkedFileUploadService implements ChunkedFileUploadService {

    private File getUploadFile(File file) {
        return new File(file.getParentFile(), "." + file.getName() + ".upload");
    }

    @Override
    public void startUpload(File file) throws IOException {
        // The rest of an interrupted upload
        Files.deleteIfExists(getUploadFile(file).toPath());
    }

    @Override
    public void uploadChunk(InputStream inputStream, File file) throws IOException {
        handleChunk(inputStream, getUploadFile(file));
    }

    @Override
    public void finishUpload(File file) throws IOException {
        // Replace the old file at once, e.g. while a composition plays it
        Files.move(getUploadFile(file).toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void handleChunk(InputStream inputStream, File file) throws IOException {
        // Check if the file exists, if not, create it
        if (!file.exists()) {
            file.createNewFile();
        }

        // Open the file in append mode
        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, length);
            }
        }
    }

}
