package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

@Service
public class DefaultLeadSheetService implements LeadSheetService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultLeadSheetService.class);

    private final SettingsService settingsService;

    public DefaultLeadSheetService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public List<LeadSheet> getAllLeadSheets() {
        List<LeadSheet> returnLeadSheetList = new ArrayList<>();
        File folder;
        File[] fileList;

        folder = new File(
                settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath());
        fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    LeadSheet leadSheet = new LeadSheet();
                    leadSheet.setName(file.getName());
                    returnLeadSheetList.add(leadSheet);
                }
            }
        }

        return returnLeadSheetList;
    }

    @Override
    public void deleteLeadSheet(String name) {
        String path = settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath() + File.separator + name;

        logger.info("Delete lead sheet '" + path + "'");

        File systemFile = new File(path);

        if (!systemFile.exists()) {
            return;
        }

        boolean result = systemFile.delete();

        if (!result) {
            logger.error("Could not delete lead sheet '" + name + "'");
        }
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }


    @Override
    public LeadSheet saveLeadSheet(InputStream uploadedInputStream, String fileName) {
        LeadSheet leadSheet = new LeadSheet();

        String path = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath() + File.separator;

        path += File.separator;

        try {
            createDirectoryIfNotExists(path);
        } catch (IOException e) {
            logger.error("Could not create directory to save the lead sheet", e);
        }

        leadSheet.setName(fileName);

        path += fileName;

        try {
            OutputStream out;
            int read;
            byte[] bytes = new byte[1024];

            out = new FileOutputStream(new File(path));

            while ((read = uploadedInputStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }

            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error("Could not save lead sheet '" + fileName + "'", e);
        }

        return leadSheet;
    }

    @Override
    public File getImage(String name) throws Exception {
        return new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath() + File.separator + name);
    }

}
