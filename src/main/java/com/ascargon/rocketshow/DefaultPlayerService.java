package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import org.apache.log4j.Logger;
import org.freedesktop.gstreamer.Gst;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DefaultPlayerService implements PlayerService {

    private final static Logger logger = Logger.getLogger(DefaultPlayerService.class);

    private NotificationService notificationService;
    private SettingsService settingsService;
    private CompositionService compositionService;
    private SetService setService;
    private SessionService sessionService;

    private CompositionPlayer defaultCompositionPlayer;
    private CompositionPlayer currentCompositionPlayer;
    private List<CompositionPlayer> sampleCompositionPlayerList = new ArrayList<>();

    private boolean autoStartNextComposition = false;

    public DefaultPlayerService(NotificationService notificationService, SettingsService settingsService, CompositionService compositionService, SetService setService, SessionService sessionService) {
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.setService = setService;
        this.sessionService = sessionService;

        try {
            Gst.init();
        } catch (Exception e) {
            logger.error("Could not initialize Gstreamer", e);
        }

        try {
            playDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not play default composition", e);
        }

        defaultCompositionPlayer = new CompositionPlayer(notificationService, this);
    }

    @Override
    public synchronized void play() throws Exception {
        if (currentCompositionPlayer.getComposition() == null) {
            return;
        }

        if (currentCompositionPlayer.getPlayState() == CompositionPlayer.PlayState.PLAYING || currentCompositionPlayer.getPlayState() == CompositionPlayer.PlayState.STOPPING
                || currentCompositionPlayer.getPlayState() == CompositionPlayer.PlayState.LOADING) {

            return;
        }

        ExecutorService playExecutor;

        // Make sure all remote devices and the local one have loaded the
        // composition before playing it
        playExecutor = Executors.newFixedThreadPool(30);

        // Load the composition on all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                playExecutor.execute(() -> remoteDevice.load(true, currentCompositionPlayer.getComposition().getName()));
            }
        }

        // Also load the local composition files
        playExecutor.execute(() -> {
            try {
                currentCompositionPlayer.loadFiles();
            } catch (Exception e) {
                logger.error("Could not load the composition files", e);
            }
        });

        logger.debug("Wait for all devices to be loaded...");

        // Wait for the compositions on all devices to be loaded
        playExecutor.shutdown();

        while (!playExecutor.isTerminated()) {
            Thread.sleep(50);
        }

        logger.debug("All devices loaded");

        if (currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.LOADED && currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.PAUSED) {
            // Maybe the composition stopped meanwhile
            return;
        }

        logger.debug("Start playing on all devices...");

        // Play the composition on all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.play();
            }
        }

        // Play the composition locally
        currentCompositionPlayer.play();

        logger.debug("Playing on all devices");
    }

    @Override
    public void playAsSample(String compositionName) throws Exception {
        // Play this composition in parallel without an option to stop/pause it
        // and without sync
        logger.trace("Play composition '" + compositionName + "' as a sample");

        // Don't allow more than a specified amount of samples to be played in
        // parallel because of performances reasons
        // TODO make the max parallel samples configurable
        if (sampleCompositionPlayerList.size() >= 20) {
            logger.debug("Not playing composition '" + compositionName
                    + "' as sample, because too many samples are already playing");

            return;
        }

        ExecutorService playExecutor;

        playExecutor = Executors.newFixedThreadPool(30);

        // Play the composition on all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                playExecutor.execute(() -> remoteDevice.playAsSample(compositionName));
            }
        }

        playExecutor.shutdown();

        // Clone the composition for each played sample (we don't want them all
        // to share the same instance) and play it
        Composition composition = compositionService
                .cloneComposition(compositionService.getComposition(compositionName));
        CompositionPlayer compositionPlayer = new CompositionPlayer(notificationService, this);
        compositionPlayer.setComposition(composition);
        compositionPlayer.setSample(true);
        sampleCompositionPlayerList.add(compositionPlayer);
        compositionPlayer.play();
    }

    @Override
    public synchronized void pause() throws Exception {
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.pause();
            }
        }

        currentCompositionPlayer.pause();
    }

    @Override
    public synchronized void togglePlay() throws Exception {
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.togglePlay();
            }
        }

        currentCompositionPlayer.togglePlay();
    }

    @Override
    public synchronized void stop(boolean playDefaultComposition) {
        if (currentCompositionPlayer.getPlayState() == CompositionPlayer.PlayState.STOPPED || currentCompositionPlayer.getPlayState() == CompositionPlayer.PlayState.STOPPING) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(30);

        // Reset the DMX universe to clear left out signals
        manager.getDmxManager().reset();

        // Stop all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                executor.execute(() -> remoteDevice.stop(playDefaultComposition));
            }
        }

        // Also stop the local composition
        executor.execute(() -> {
            try {
                currentCompositionPlayer.stop(playDefaultComposition);
            } catch (Exception e) {
                logger.error("Could not load the composition files", e);
            }
        });

        // Wait for all devices to be stopped
        executor.shutdown();

        while (!executor.isTerminated()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                logger.error("Error while waiting to stop the composition", e);
            }
        }
    }

    @Override
    public synchronized void stop() {
        stop(true);
    }

    @Override
    public synchronized void seek(long positionMillis) throws Exception {
        if (currentCompositionPlayer != null) {
            currentCompositionPlayer.seek(positionMillis);
        }
    }

    public synchronized void playDefaultComposition() throws Exception {
        if (defaultCompositionPlayer.getComposition() != null) {
            // The default composition is already initialized
            return;
        }

        defaultCompositionPlayer = new CompositionPlayer(notificationService, this);

        if (settingsService.getSettings().getDefaultComposition() == null || settingsService.getSettings().getDefaultComposition().length() == 0) {
            return;
        }

        logger.info("Play default composition");

        defaultCompositionPlayer.setComposition(compositionService.getComposition(settingsService.getSettings().getDefaultComposition()));
        defaultCompositionPlayer.setDefaultComposition(true);
        defaultCompositionPlayer.play();
    }

    public synchronized void stopDefaultComposition() throws Exception {
        if (defaultCompositionPlayer.getComposition() == null) {
            return;
        }

        logger.debug("Stopping the default composition...");

        defaultCompositionPlayer.stop();
        defaultCompositionPlayer.setComposition(null);
    }

    public CompositionPlayer.PlayState getPlayState() {
        return currentCompositionPlayer.getPlayState();
    }

    public String getCompositionName() {
        if (currentCompositionPlayer.getComposition() == null) {
            return null;
        }

        return currentCompositionPlayer.getComposition().getName();
    }

    public long getCompositionDurationMillis() {
        if (currentCompositionPlayer.getComposition() == null) {
            return 0;
        }

        return currentCompositionPlayer.getComposition().getDurationMillis();
    }

    public void close() throws Exception {
        currentCompositionPlayer.close();

        for (CompositionPlayer sampleCompositionPlayer : sampleCompositionPlayerList) {
            sampleCompositionPlayer.close();
        }
    }

    @Override
    public void compositionPlayerFinishedPlaying(CompositionPlayer compositionPlayer) throws Exception {
        if (compositionPlayer.isSample()) {
            sampleCompositionPlayerList.remove(compositionPlayer);
            return;
        }

        if (autoStartNextComposition && setService.hasNextComposition()) {
            // Stop, don't play the default composition but start playing the next composition
            stop(false);

            setService.nextComposition(false);
            play();
        } else if (sessionService.getSession().isAutoSelectNextComposition()) {
            manager.getFileCompositionService().nextComposition();
        } else {
            // TODO What's the difference between this else and the last else if??

            // Stop, play the default composition and select the
            // next composition automatically (if there is one)
            stop(true);
            setService.nextComposition();
        }
    }

    public long getPositionMillis() {
        return currentCompositionPlayer.getPositionMillis();
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

        currentCompositionPlayer.setComposition(composition);

        notificationService.notifyClients();
    }

    public void setCurrentComposition(Composition currentComposition) throws Exception {
        setComposition(currentComposition, true, false);
    }

    public void setCompositionName(String name) throws Exception {
        setCurrentComposition(compositionService.getComposition(name));
    }

    public void setAutoStartNextComposition(boolean autoStartNextComposition) {
        this.autoStartNextComposition = autoStartNextComposition;
    }

}
