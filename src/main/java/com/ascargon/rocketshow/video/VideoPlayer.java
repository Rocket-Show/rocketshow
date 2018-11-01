package com.ascargon.rocketshow.video;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.Pipeline;

import com.ascargon.rocketshow.composition.PlayerLoadedListener;
import com.ascargon.rocketshow.util.ShellManager;

public class VideoPlayer {

	final static Logger logger = Logger.getLogger(VideoPlayer.class);

	private ShellManager shellManager;

	private Timer loadTimer;
	private boolean loop;
	private String path;

	public void load(PlayerLoadedListener playerLoadedListener, String path, long positionMillis, Pipeline pipeline)
			throws IOException, InterruptedException {
		
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
		}, 2000 /* TODO Specify in global config */);
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
	}

}
