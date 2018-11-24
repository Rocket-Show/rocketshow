package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import com.ascargon.rocketshow.SettingsService;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.springframework.stereotype.Service;

/**
 * Prepares the log directory and sends it to the download.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultLogDownloadService implements LogDownloadService {

	private final static Logger logger = LoggerFactory.getLogger(DefaultLogDownloadService.class);

	private final SettingsService settingsService;

	private final static String LOGS_FILE_NAME = "logs.zip";

	public DefaultLogDownloadService(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@Override
	public File getLogsFile() throws Exception {
		// Prepare the log directory for download
		ShellManager shellManager = new ShellManager(new String[] { "bash", "-c",
				"zip -r -j " + settingsService.getSettings().getBasePath() + "/" + LOGS_FILE_NAME + " " + settingsService.getSettings().getBasePath() + "log/*" });

		BufferedReader reader = new BufferedReader(new InputStreamReader(shellManager.getInputStream()));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				logger.debug("Output from log prepare process: " + line);
			}
		} catch (IOException e) {
			logger.error("Could not read log prepare process output", e);
		}

		shellManager.getProcess().waitFor();

		// Return the prepared zip
		return new File(settingsService.getSettings().getBasePath() + "/" + LOGS_FILE_NAME);
	}

}
