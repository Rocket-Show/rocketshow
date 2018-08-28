package com.ascargon.rocketshow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.Composition.PlayState;

public class Player {

	final static Logger logger = Logger.getLogger(Player.class);

	private Composition composition;
	private Manager manager;

	public Player(Manager manager) {
		this.manager = manager;
	}

	private long getSeekMillis(long millis) {
		// Round bottom to a full second, because seeking to milliseconds is
		// currently not supported by audio- and videoplayer.
		return millis - (millis % 1000);
	}

	public void load(long positionMillis) throws Exception {
		if (composition != null) {
			composition.loadFiles(positionMillis);
		}
	}

	public void play(long positionMillis) throws Exception {
		if (composition == null) {
			return;
		}

		if (composition.getPlayState() == PlayState.PLAYING || composition.getPlayState() == PlayState.STOPPING
				|| composition.getPlayState() == PlayState.LOADING) {

			return;
		}

		// Initialize the final variable (enclosing scope)
		final long loadAtPositionMillis = positionMillis;

		ExecutorService playExecutor;

		// Make sure all remote devices and the local one have loaded the
		// composition before playing it
		playExecutor = Executors.newFixedThreadPool(30);

		// Load the composition on all remote devices
		for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				playExecutor.execute(new Runnable() {
					public void run() {
						remoteDevice.load(true, composition.getName(), loadAtPositionMillis);
					}
				});
			}
		}

		// Also load the local composition files
		playExecutor.execute(new Runnable() {
			public void run() {
				try {
					composition.loadFiles(loadAtPositionMillis);
				} catch (Exception e) {
					logger.error("Could not load the composition files", e);
				}
			}
		});

		logger.debug("Wait for all devices to be loaded...");

		// Wait for the compositions on all devices to be loaded
		playExecutor.shutdown();

		while (!playExecutor.isTerminated()) {
		}

		logger.debug("All devices loaded");

		if (composition.getPlayState() != PlayState.LOADED) {
			// Maybe the composition stopped meanwhile
			return;
		}

		logger.debug("Start playing on all devices...");

		// Play the composition on all remote devices
		for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				remoteDevice.play();
			}
		}

		// Play the composition locally
		if (composition != null) {
			composition.play();
		}

		logger.debug("Playing on all devices");
	}

	public void play() throws Exception {
		this.play(0);
	}

	public void pause() throws Exception {
		for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				remoteDevice.pause();
			}
		}

		if (composition != null) {
			// Seek to the correct position to avoid desync (stop and load
			// again)
			composition.setPlayState(PlayState.PAUSED);
			seek(composition.getPositionMillis());
		}
	}

	public void togglePlay() throws Exception {
		for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				remoteDevice.togglePlay();
			}
		}

		if (composition != null) {
			composition.togglePlay();
		}
	}

	public void stop(boolean playDefaultComposition, boolean restartAfter) throws Exception {
		if (composition == null) {
			return;
		}

		if (composition.getPlayState() == PlayState.STOPPED || composition.getPlayState() == PlayState.STOPPING) {
			return;
		}

		ExecutorService executor = Executors.newFixedThreadPool(30);

		// Reset the DMX universe to clear left out signals
		manager.getDmxManager().reset();

		// Stop all remote devices
		for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
			if (remoteDevice.isSynchronize()) {
				executor.execute(new Runnable() {
					public void run() {
						remoteDevice.stop(playDefaultComposition, restartAfter);
					}
				});
			}
		}

		// Also stop the local composition
		executor.execute(new Runnable() {
			public void run() {
				try {
					composition.stop(playDefaultComposition, restartAfter);
				} catch (Exception e) {
					logger.error("Could not load the composition files", e);
				}
			}
		});

		// Wait for all devices to be stopped
		executor.shutdown();

		while (!executor.isTerminated()) {
		}
	}

	public void stop(boolean playDefaultComposition) throws Exception {
		stop(playDefaultComposition, false);
	}

	public void stop() throws Exception {
		stop(true, false);
	}

	public void seek(long positionMillis) throws Exception {
		// Seek to a specified position and continue playing the composition, if
		// necessary.
		if (composition.getPlayState() == PlayState.STOPPING || composition.getPlayState() == PlayState.LOADING) {
			return;
		}

		composition.setPositionMillis(getSeekMillis(positionMillis));

		PlayState currentPlayState = composition.getPlayState();

		// Stop the current composition and load it again to avoid desync of the
		// files
		stop(false, true);

		if (currentPlayState == PlayState.PLAYING) {
			play(composition.getPositionMillis());
		} else {
			load(composition.getPositionMillis());
		}
	}

	public PlayState getPlayState() {
		if (composition == null) {
			return PlayState.STOPPED;
		}

		return composition.getPlayState();
	}

	public String getCompositionName() {
		if (composition == null) {
			return null;
		}

		return composition.getName();
	}

	public long getCompositionDurationMillis() {
		if (composition == null) {
			return 0;
		}

		return composition.getDurationMillis();
	}

	public long getPositionMillis() {
		if (composition == null) {
			return 0;
		}

		return composition.getPositionMillis();
	}

	public void close() throws Exception {
		if (composition != null) {
			composition.close();
		}
	}

	public void setComposition(Composition composition, boolean playDefaultCompositionWhenStoppingComposition,
			boolean forceLoad) throws Exception {

		if (composition == null) {
			return;
		}

		if (composition.getName().equals(this.getCompositionName()) && !forceLoad) {
			// This composition is already loaded, don't stop/load again
			return;
		}

		// Stop the current composition, if needed
		stop(playDefaultCompositionWhenStoppingComposition);

		this.composition = composition;

		manager.getStateManager().notifyClients();
	}

	public void setComposition(Composition composition) throws Exception {
		setComposition(composition, true, false);
	}

	public void setCompositionName(String name) throws Exception {
		setComposition(manager.getCompositionManager().getComposition(name));
		;
	}

	public void loadFiles(long positionMillis) throws Exception {
		if (composition == null) {
			return;
		}

		composition.loadFiles(positionMillis);
	}

	public void setAutoStartNextComposition(boolean autoStartNextComposition) {
		if (composition == null) {
			return;
		}

		composition.setAutoStartNextComposition(autoStartNextComposition);
	}

}
