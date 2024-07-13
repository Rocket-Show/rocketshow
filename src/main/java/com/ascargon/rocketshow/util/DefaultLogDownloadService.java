package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.SettingsService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Prepares the log directory and sends it to the download.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultLogDownloadService implements LogDownloadService {

    private final SettingsService settingsService;
    private final ZipService zipService;

    public DefaultLogDownloadService(
            SettingsService settingsService,
            ZipService zipService
    ) {
        this.settingsService = settingsService;
        this.zipService = zipService;
    }

    @Override
    public File getLogsFile() throws Exception {
        // zip the log directory
        FileOutputStream fileOutputStream = new FileOutputStream(LOGS_FILE_NAME);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        File fileToZip = new File(settingsService.getSettings().getBasePath() + "log");

        zipService.zipFile(fileToZip, fileToZip.getName(), zipOutputStream, null);
        zipOutputStream.close();
        fileOutputStream.close();

        // Return the prepared zip
        return new File(settingsService.getSettings().getBasePath() + LOGS_FILE_NAME);
    }

}
