package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.settings.SettingsService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
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
        File logsFile = new File(settingsService.getSettings().getBasePath() + LOGS_FILE_NAME);
        File fileToZip = new File(settingsService.getSettings().getBasePath() + "log");

        try (FileOutputStream fileOutputStream = new FileOutputStream(logsFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            zipService.zipFile(fileToZip, fileToZip.getName(), zipOutputStream, null);
        }

        // Return the prepared zip
        return logsFile;
    }

}
