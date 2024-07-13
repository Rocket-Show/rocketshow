package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.*;
import java.io.*;

@Service
public class DefaultChunkedFileUploadService implements ChunkedFileUploadService {

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
