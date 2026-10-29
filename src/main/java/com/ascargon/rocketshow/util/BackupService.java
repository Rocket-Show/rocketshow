package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.InputStream;

@Service
public interface BackupService {

    // Writes the backup into the stream, e.g. the download
    void create(OutputStream outputStream) throws Exception;

    void restoreInit() throws Exception;

    void restoreAddChunk(InputStream inputStream) throws Exception;

    void restoreFinish() throws Exception;

}
