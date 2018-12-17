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

    private final static String LOGS_FILE_NAME = "logs.zip";

    public DefaultLogDownloadService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // Taken from https://www.baeldung.com/java-compress-and-uncompress
    private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }

        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }

            File[] children = fileToZip.listFiles();

            if (children != null) {
                for (File childFile : children) {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
                }
            }

            return;
        }

        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;

        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }

        fis.close();
    }

    @Override
    public File getLogsFile() throws Exception {
        // Zip the logfile directory
        FileOutputStream fileOutputStream = new FileOutputStream(LOGS_FILE_NAME);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        File fileToZip = new File(settingsService.getSettings().getBasePath() + "log");

        zipFile(fileToZip, fileToZip.getName(), zipOutputStream);
        zipOutputStream.close();
        fileOutputStream.close();

        // Return the prepared zip
        return new File(settingsService.getSettings().getBasePath() + "/" + LOGS_FILE_NAME);
    }

}
