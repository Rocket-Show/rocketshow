package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
public interface ChunkedFileUploadService {

    void handleChunk(InputStream inputStream, File file) throws IOException;

    // Upload a file in chunks. The chunks are collected in a hidden file next to it, so a file which is already there
    // stays untouched until the new one is complete. An interrupted upload leaves no half file behind.
    void startUpload(File file) throws IOException;

    void uploadChunk(InputStream inputStream, File file) throws IOException;

    void finishUpload(File file) throws IOException;

    // Whether the file is a hidden file of an upload, which is not complete yet
    static boolean isUploadFile(String fileName) {
        return fileName.startsWith(".") && fileName.endsWith(".upload");
    }

}
