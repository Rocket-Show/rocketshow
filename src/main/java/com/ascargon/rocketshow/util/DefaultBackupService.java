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
 * Manages backups.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultBackupService implements BackupService {

    private final SettingsService settingsService;

    private final static String LOGS_FILE_NAME = "logs.zip";

    public DefaultBackupService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public File createBackup() throws Exception {
        // TODO Check free disk space at least 50 % (because we use it for the backup file temporarily)

        // Zip the logfile directory
        FileOutputStream fileOutputStream = new FileOutputStream(LOGS_FILE_NAME);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        File fileToZip = new File(settingsService.getSettings().getBasePath() + "log");

        // TODO extract zipService from logDownloadService
//        zipFile(fileToZip, fileToZip.getName(), zipOutputStream);
//        zipOutputStream.close();
//        fileOutputStream.close();

        // Return the prepared zip
//        return new File(settingsService.getSettings().getBasePath() + File.separator + LOGS_FILE_NAME);
        return null;
    }

}
