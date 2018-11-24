package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public interface LogDownloadService {

    File getLogsFile() throws Exception;

}
