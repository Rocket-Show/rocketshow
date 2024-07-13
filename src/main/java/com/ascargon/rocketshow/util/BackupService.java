package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;

@Service
public interface BackupService {

    File create() throws Exception;

    void restoreInit() throws Exception;

    void restoreAddChunk(InputStream inputStream) throws Exception;

    void restoreFinish() throws Exception;

}
