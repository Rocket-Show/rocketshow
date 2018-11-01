package com.ascargon.rocketshow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.Composition.PlayState;

public class Player {

    final static Logger logger = Logger.getLogger(Player.class);

    private Composition composition;
    private List<Composition> sampleCompositionList = new ArrayList<Composition>();
    private Manager manager;

    public Player(Manager manager) {
        this.manager = manager;
    }

    public void load() throws Exception {
        if (composition != null) {
            composition.loadFiles();
        }
    }

    public void play() throws Exception {
        if (composition == null) {
            return;
        }

        if (composition.getPlayState() == PlayState.PLAYING || composition.getPlayState() == PlayState.STOPPING
                || composition.getPlayState() == PlayState.LOADING) {

            return;
        }

        ExecutorService playExecutor;

        // Make sure all remote devices and the local one have loaded the
        // composition before playing it
        playExecutor = Executors.newFixedThreadPool(30);

        // Load the composition on all remote devices
        for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                playExecutor.execute(new Runnable() {
                    public void run() {
                        remoteDevice.load(true, composition.getName());
                    }
                });
            }
        }

        // Also load the local composition files
        playExecutor.execute(new Runnable() {
            public void run() {
                try {
                    composition.loadFiles();
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

        if (composition.getPlayState() != PlayState.LOADED && composition.getPlayState() != PlayState.PAUSED) {
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

    public void playAsSample(String compositionName) throws Exception {
        // Play this composition in parallel without an option to stop/pause it
        // and without sync
        logger.trace("Play composition '" + compositionName + "' as a sample");

        // Don't allow more than a specified amount of samples to be played in
        // parallel because of performances reasons
        // TODO make the max parallel samples configurable
        if (sampleCompositionList.size() >= 20) {
            logger.debug("Not playing composition '" + compositionName
                    + "' as sample, because too many samples are already playing");

            return;
        }

        ExecutorService playExecutor;

        playExecutor = Executors.newFixedThreadPool(30);

        // Play the composition on all remote devices
        for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                playExecutor.execute(new Runnable() {
                    public void run() {
                        remoteDevice.playAsSample(compositionName);
                    }
                });
            }
        }

        playExecutor.shutdown();

        // Clone the composition for each played sample (we don't want them all
        // to share the same instance) and play it
        Composition composition = manager.getCompositionManager()
                .cloneComposition(manager.getCompositionManager().getComposition(compositionName));
        composition.setSample(true);
        sampleCompositionList.add(composition);
        composition.play();
    }

    public void pause() throws Exception {
        for (RemoteDevice remoteDevice : manager.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.pause();
            }
        }

        if (composition != null) {
            // Seek to the correct position
            composition.pause();
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

    public void stop(boolean playDefaultComposition) throws Exception {
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
                        remoteDevice.stop(playDefaultComposition);
                    }
                });
            }
        }

        // Also stop the local composition
        executor.execute(new Runnable() {
            public void run() {
                try {
                    composition.stop(playDefaultComposition);
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

    public void stop() throws Exception {
        stop(true);
    }

    public void seek(long positionMillis) throws Exception {
        composition.seek(positionMillis);
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

        for (Composition sampleComposition : sampleCompositionList) {
            sampleComposition.close();
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
    }

    public void setAutoStartNextComposition(boolean autoStartNextComposition) {
        if (composition == null) {
            return;
        }

        composition.setAutoStartNextComposition(autoStartNextComposition);
    }

    public void sampleCompositionFinishedPlaying(Composition composition) {
        sampleCompositionList.remove(composition);
    }

}
