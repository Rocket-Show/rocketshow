package com.ascargon.rocketshow.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;

import com.ascargon.rocketshow.api.NotificationService;
import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

@XmlRootElement
public class Updater {

	public enum UpdateState {
		DOWNLOADING, INSTALLING, REBOOTING
	}

	private final static Logger logger = Logger.getLogger(Updater.class);

	private final static String UPDATE_PATH = "update/";
	private final static String BEFORE_SCRIPT_NAME = "before.sh";
	private final static String AFTER_SCRIPT_NAME = "after.sh";
	private final static String WAR_NAME = "current.war";
	private final static String CURRENT_VERSION = "currentversion.xml";
	private final static String UPDATE_URL = "https://www.rocketshow.net/update/";
	private final static String UPDATE_SCRIPT = "update.sh";

	private NotificationService notificationService;
	private Manager manager;

	public Updater(NotificationService notificationService, Manager manager) {
		this.notificationService = notificationService;
		this.manager = manager;
	}

	public VersionInfo getCurrentVersionInfo() throws Exception {
		File file = new File(Manager.BASE_PATH + CURRENT_VERSION);

		JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		return (VersionInfo) jaxbUnmarshaller.unmarshal(file);
	}

	public VersionInfo getRemoteVersionInfo() throws Exception {
		URL url = new URL(UPDATE_URL + "currentversion.xml");
		InputStream inputStream = url.openStream();

		JAXBContext jaxbContext = JAXBContext.newInstance(VersionInfo.class);

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		return (VersionInfo) jaxbUnmarshaller.unmarshal(inputStream);
	}

	private void downloadUpdateFile(String name) throws Exception {
		URL url = new URL(UPDATE_URL + name);
		ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
		FileOutputStream fileOutputStream = new FileOutputStream(Manager.BASE_PATH + UPDATE_PATH + name);
		fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		fileOutputStream.close();
	}

	private void executeScript(String[] command) throws Exception {
		Process process = new ProcessBuilder(command).start();
		process.waitFor();
		process.destroy();
	}

	public void update() throws Exception {
		logger.info("Updating system...");
		
		manager.getSession().setUpdateFinished(false);
		manager.saveSession();

		logger.info("Downloading new version...");

		notificationService.notifyClients(UpdateState.DOWNLOADING);

		// Download the new version
		downloadUpdateFile(CURRENT_VERSION);
		downloadUpdateFile(WAR_NAME);
		downloadUpdateFile(BEFORE_SCRIPT_NAME);
		downloadUpdateFile(AFTER_SCRIPT_NAME);

		notificationService.notifyClients(UpdateState.INSTALLING);

		// Execute the script
		logger.info("Files downloaded. Execute update...");
		executeScript(new String[] { Manager.BASE_PATH + UPDATE_SCRIPT });

		notificationService.notifyClients(UpdateState.REBOOTING);

		// After the reboot, the new status will be update finished and this
		// status should be dismissed
		manager.getSession().setUpdateFinished(true);
		manager.saveSession();
		manager.reboot();
	}

}
