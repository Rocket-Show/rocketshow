package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@Service
public interface ChunkedFileUploadService {

    void handleChunk(InputStream inputStream, File file) throws IOException;

}
