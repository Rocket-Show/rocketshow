package com.ascargon.rocketshow.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

/**
 * Prepares the log directory and sends it to the download.
 *
 * @author Moritz A. Vieli
 */
public class LogDownload {

	final static Logger logger = Logger.getLogger(LogDownload.class);

	public final static String LOGS_FILE_NAME = "logs.zip";

	public static File getLogsFile() throws Exception {
		// Prepare the log directory for download
		ShellManager shellManager = new ShellManager(new String[] { "bash", "-c",
				"zip -r -j " + Manager.BASE_PATH + LOGS_FILE_NAME + " " + Manager.BASE_PATH + "log/*" });

		BufferedReader reader = new BufferedReader(new InputStreamReader(shellManager.getInputStream()));
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				logger.debug("Output from log prepare process: " + line);
			}
		} catch (IOException e) {
			logger.error("Could not read log prepare process output", e);
		}

		shellManager.getProcess().waitFor();

		// Return the prepared zip
		return new File(Manager.BASE_PATH + LOGS_FILE_NAME);
	}

}
