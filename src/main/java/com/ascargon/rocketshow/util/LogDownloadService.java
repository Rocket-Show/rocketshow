package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public interface LogDownloadService {

    String LOGS_FILE_NAME = "logs.zip";

    File getLogsFile() throws Exception;

}
