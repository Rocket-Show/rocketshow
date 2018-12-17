package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.SessionService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.api.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

@Service
public class DefaultUpdateService implements UpdateService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultUpdateService.class);

    private final static String UPDATE_PATH = "update/";
    private final static String BEFORE_SCRIPT_NAME = "before.sh";
    private final static String AFTER_SCRIPT_NAME = "after.sh";
    private final static String JAR_NAME = "rocketshow.jar";
    private final static String CURRENT_VERSION = "currentversion2.xml";
    private final static String UPDATE_URL = "https://www.rocketshow.net/update/";
    private final static String UPDATE_SCRIPT = "update.sh";

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
        File file = new File(settingsService.getSettings().getBasePath() + "/" + CURRENT_VERSION);

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (VersionInfo) jaxbUnmarshaller.unmarshal(file);
    }

    @Override
    public VersionInfo getRemoteVersionInfo() throws Exception {
        URL url = new URL(UPDATE_URL + "currentversion2.xml");
        InputStream inputStream = url.openStream();

        JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        return (VersionInfo) jaxbUnmarshaller.unmarshal(inputStream);
    }

    private void downloadUpdateFile(String name) throws Exception {
        URL url = new URL(UPDATE_URL + name);
        ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
        FileOutputStream fileOutputStream = new FileOutputStream(settingsService.getSettings().getBasePath() + "/" + UPDATE_PATH + name);
        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        fileOutputStream.close();
    }

    private void executeScript(String[] command) throws Exception {
        Process process = new ProcessBuilder(command).start();
        process.waitFor();
        process.destroy();
    }

    @Override
    public void update() throws Exception {
        logger.info("Updating system...");

        sessionService.getSession().setUpdateFinished(false);
        sessionService.save();

        logger.info("Downloading new version...");

        notificationService.notifyClients(UpdateState.DOWNLOADING);

        // Download the new version
        downloadUpdateFile(CURRENT_VERSION);
        downloadUpdateFile(JAR_NAME);
        downloadUpdateFile(BEFORE_SCRIPT_NAME);
        downloadUpdateFile(AFTER_SCRIPT_NAME);

        notificationService.notifyClients(UpdateState.INSTALLING);

        // Execute the script
        logger.info("Files downloaded. Execute update...");
        executeScript(new String[]{settingsService.getSettings().getBasePath() + "/" + UPDATE_SCRIPT});

        notificationService.notifyClients(UpdateState.REBOOTING);

        // After the reboot, the new status will be update finished and this
        // status should be dismissed
        sessionService.getSession().setUpdateFinished(true);
        sessionService.save();

        rebootService.reboot();
    }

}
