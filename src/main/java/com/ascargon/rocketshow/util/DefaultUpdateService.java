package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DefaultUpdateService implements UpdateService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultUpdateService.class);

    private final static String CURRENT_VERSION = "currentversion2.xml";
    private final static String UPDATE_URL = "https://www.rocketshow.net/update/";
    private final static String UPDATE_URL_TEST_SUFFIX = "test/";

    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final SessionService sessionService;
    private final RebootService rebootService;

    public DefaultUpdateService(NotificationService notificationService, SettingsService settingsService, SessionService sessionService, RebootService rebootService) {
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.sessionService = sessionService;
        this.rebootService = rebootService;
    }

    @Override
    public VersionInfo getCurrentVersionInfo() throws Exception {
        File file = new File(settingsService.getSettings().getBasePath() + File.separator + CURRENT_VERSION);

        if (!file.exists()) {
            return new VersionInfo(); // return empty object if file does not exist
        }

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

        return (VersionInfo) jaxbUnmarshaller.unmarshal(file);
    }

    private String getRemoteBaseUrl(boolean testBranch) {
        String url = UPDATE_URL;
        if (testBranch) {
            url += UPDATE_URL_TEST_SUFFIX;
        }
        return url;
    }

    @Override
    public VersionInfo getRemoteVersionInfo(boolean testBranch) throws Exception {
        URL url = new URL(getRemoteBaseUrl(testBranch) + "currentversion2.xml");
        InputStream inputStream = url.openStream();

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (VersionInfo) jaxbUnmarshaller.unmarshal(inputStream);
    }

    private void executeScript(String[] command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        process.waitFor();
        process.destroy();
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    @Override
    public void update(boolean testBranch) throws Exception {
        logger.info("Updating system...");

        sessionService.getSession().setUpdateFinished(false);
        sessionService.save();

        logger.info("Downloading new version...");

        // TODO use RAUC

        notificationService.notifyClients(UpdateState.DOWNLOADING);
        // TODO
        notificationService.notifyClients(UpdateState.INSTALLING);

        // Execute the script
        logger.info("Files downloaded. Execute update...");
        // TODO
//        executeScript(new String[]{settingsService.getSettings().getBasePath() + File.separator + UPDATE_SCRIPT});

        notificationService.notifyClients(UpdateState.REBOOTING);

        // After the reboot, the new status will be update finished and this
        // status should be dismissed
        sessionService.getSession().setUpdateFinished(true);
        sessionService.save();

        // Wait for the save to be completed (sometimes, the flag is missing otherwise)
        Thread.sleep(3000);

        rebootService.reboot();
    }

}