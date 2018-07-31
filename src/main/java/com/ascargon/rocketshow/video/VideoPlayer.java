package com.ascargon.rocketshow.video;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.composition.PlayerLoadedListener;
import com.ascargon.rocketshow.util.ShellManager;

public class VideoPlayer {

	final static Logger logger = Logger.getLogger(VideoPlayer.class);

	private ShellManager shellManager;

	private Timer loadTimer;
	private Timer closeTimer;
	private boolean loop;
	private String path;

	private String getTimeFromPositionMillis(long positionMillis) {
		//long millis = positionMillis % 1000;
		long second = (positionMillis / 1000) % 60;
		long minute = (positionMillis / (1000 * 60)) % 60;
		long hour = (positionMillis / (1000 * 60 * 60)) % 24;

		// Return the string including millis
		//String time = String.format("%02d:%02d:%02d.%d", hour, minute, second, millis);
		
		// Return the string without millis
		return String.format("%02d:%02d:%02d", hour, minute, second);
	}
	
	public void load(PlayerLoadedListener playerLoadedListener, String path, long positionMillis) throws IOException, InterruptedException {
		logger.debug("Loading video '" + path + "'");

		this.path = path;

		List<String> params = new ArrayList<String>();
		params.add("omxplayer");
		params.add(path);

		// Adjust framerate/resolution to video
		params.add("-r");

		// Set background to black
		params.add("-b");

		// Set the start position
		params.add("--pos");
		params.add(getTimeFromPositionMillis(positionMillis));
		
		if (loop) {
			params.add("--loop");
		}

		shellManager = new ShellManager(params.toArray(new String[0]));

		new Thread(new Runnable() {
			public void run() {
				BufferedReader reader = new BufferedReader(new InputStreamReader(shellManager.getInputStream()));
				String line = null;
				try {
					while ((line = reader.readLine()) != null) {
						logger.debug("Output from video player: " + line);
					}
				} catch (IOException e) {
					logger.error("Could not read video player output", e);
				}
			}
		}).start();

		pause();

		// Wait for the player to get ready, because reading the input stream
		// does not work. Output will only be received on exit.
		loadTimer = new Timer();
		loadTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (loadTimer != null) {
					loadTimer.cancel();
				}
				loadTimer = null;

				if (playerLoadedListener != null) {
					playerLoadedListener.playerLoaded();
				}
			}
		}, 1000 /* TODO Specify in global config */);
	}

	public void play() throws IOException {
		shellManager.sendCommand("p");
	}

	public void pause() throws IOException {
		shellManager.sendCommand("p");
	}

	public void resume() throws IOException {
		shellManager.sendCommand("p");
	}

	private void startCloseTimer() {
		// Try killing the Omxplayer process continuously
		closeTimer = new Timer();
		closeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					Process process = new ProcessBuilder("ps", "-ef").start();

					new Thread(new Runnable() {
						public void run() {
							BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
							String line = null;

							try {
								while ((line = reader.readLine()) != null) {
									if (line.contains("/usr/bin/omxplayer.bin")) {
										new ProcessBuilder("kill", "-9", line.substring(9, 15).trim()).start();
									}
								}
							} catch (IOException e) {
								logger.error("Could not read video player output", e);
							}
						}
					}).start();
				} catch (IOException e) {
					logger.error("Could not stop the video player", e);
				}
			}
		}, 0, 100);
	}

	public void stop() throws Exception {
		// Exiting Omxplayer with q does not work sometimes (fast play/stop).
		// The thread simply hangs for about 30 seconds. We therefore kill -9
		// the process.

		// We don't use process.destroy() or process.destroyForcibly(). Both
		// methods sometimes leave the player but pass by waitFor(), which
		// results in zombie-Omxplayers still consuming resources.
		startCloseTimer();

		if (shellManager != null) {
			logger.debug("Wait for process shutdown...");
			shellManager.getProcess().waitFor();
			shellManager.close();

			logger.debug("File '" + path + "' stopped");
		}

		if (closeTimer != null) {
			closeTimer.cancel();
			closeTimer = null;
		}
	}

	public void close() throws Exception {
		stop();
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
	}

}
