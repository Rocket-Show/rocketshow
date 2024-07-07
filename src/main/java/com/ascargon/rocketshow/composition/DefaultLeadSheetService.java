package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.ChunkedFileUploadService;
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
    private final ChunkedFileUploadService chunkedFileUploadService;

    public DefaultLeadSheetService(
            SettingsService settingsService,
            ChunkedFileUploadService chunkedFileUploadService
    ) {
        this.settingsService = settingsService;
        this.chunkedFileUploadService = chunkedFileUploadService;
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

    private File saveLeadSheetGetFile(String fileName) throws Exception {
        String path = settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath() + File.separator;

        path += File.separator;

        try {
            createDirectoryIfNotExists(path);
        } catch (IOException e) {
            logger.error("Could not create directory to save the lead sheet", e);
        }

        path += fileName;

        return new File(path);
    }

    @Override
    public void saveLeadSheetInit(String fileName) throws Exception {
        File file = saveLeadSheetGetFile(fileName);
        if (file.exists()) {
            boolean result = file.delete();
            if (!result) {
                throw new Exception("Could not delete lead sheet '" + file.getPath() + "'");
            }
        }
    }

    @Override
    public void saveLeadSheetAddChunk(InputStream inputStream, String fileName) throws Exception {
        chunkedFileUploadService.handleChunk(inputStream, saveLeadSheetGetFile(fileName));
    }

    @Override
    public LeadSheet saveLeadSheetFinish(String fileName) throws Exception {
        LeadSheet leadSheet = new LeadSheet();
        leadSheet.setName(fileName);
        return leadSheet;
    }

    @Override
    public File getImage(String name) throws Exception {
        return new File(settingsService.getSettings().getBasePath() + File.separator + settingsService.getSettings().getMediaPath() + File.separator + settingsService.getSettings().getLeadSheetPath() + File.separator + name);
    }

}
