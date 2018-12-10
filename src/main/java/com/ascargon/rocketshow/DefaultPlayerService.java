package com.ascargon.rocketshow;

import com.ascargon.rocketshow.api.ActivityNotificationMidiService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionPlayer;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.dmx.DmxService;
import com.ascargon.rocketshow.midi.MidiRoutingService;
import com.ascargon.rocketshow.util.OperatingSystemInformationService;
import org.freedesktop.gstreamer.Gst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DefaultPlayerService implements PlayerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultPlayerService.class);

    private final NotificationService notificationService;
    private final ActivityNotificationMidiService activityNotificationMidiService;
    private final SettingsService settingsService;
    private final CompositionService compositionService;
    private final SetService setService;
    private final SessionService sessionService;
    private final MidiRoutingService midiRoutingService;
    private final DmxService dmxService;
    private final CapabilitiesService capabilitiesService;
    private final OperatingSystemInformationService operatingSystemInformationService;

    private CompositionPlayer defaultCompositionPlayer;
    private final CompositionPlayer currentCompositionPlayer;
    private final List<CompositionPlayer> sampleCompositionPlayerList = new ArrayList<>();

    public DefaultPlayerService(NotificationService notificationService, ActivityNotificationMidiService activityNotificationMidiService, SettingsService settingsService, CompositionService compositionService, SetService setService, SessionService sessionService, MidiRoutingService midiRoutingService, DmxService dmxService, CapabilitiesService capabilitiesService, OperatingSystemInformationService operatingSystemInformationService) {
        this.notificationService = notificationService;
        this.activityNotificationMidiService = activityNotificationMidiService;
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.setService = setService;
        this.sessionService = sessionService;
        this.midiRoutingService = midiRoutingService;
        this.dmxService = dmxService;
        this.capabilitiesService = capabilitiesService;
        this.operatingSystemInformationService = operatingSystemInformationService;

        currentCompositionPlayer = new CompositionPlayer(notificationService, activityNotificationMidiService, this, settingsService, midiRoutingService, capabilitiesService, operatingSystemInformationService);
        defaultCompositionPlayer = new CompositionPlayer(notificationService, activityNotificationMidiService, this, settingsService, midiRoutingService, capabilitiesService, operatingSystemInformationService);

        try {
            Gst.init();
        } catch (Exception | UnsatisfiedLinkError e) {
            // Gstreamer might not be installed properly or not be installed at all
            logger.error("Could not initialize Gstreamer", e);
            capabilitiesService.getCapabilities().setGstreamer(false);
        }

        try {
            playDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not play default composition", e);
        }

        defaultCompositionPlayer = new CompositionPlayer(notificationService, activityNotificationMidiService, this, settingsService, midiRoutingService, capabilitiesService, operatingSystemInformationService);

        // Load the last set/composition
        try {
            if (sessionService.getSession() != null && sessionService.getSession().getCurrentSetName() != null && sessionService.getSession().getCurrentSetName().length() > 0) {
                // Load the last set
                loadSetAndComposition(sessionService.getSession().getCurrentSetName());
            } else {
                // Load the default set
                loadSetAndComposition("");
            }
        } catch (Exception e) {
            logger.error("Could not load the last set from the session", e);
        }
    }

    @Override
    public void loadSetAndComposition(String setName) throws Exception {
        if (setName.length() > 0) {
            setService.setCurrentSet(compositionService.getSet(setName));
        }

        // Read the current composition file
        if (setService.getCurrentSet() == null) {
            // We have no set. Simply read the first composition, if available
            logger.debug("Try setting an initial composition...");

            List<Composition> compositions = compositionService.getAllCompositions();

            if (compositions.size() > 0) {
                logger.debug("Set initial composition '" + compositions.get(0).getName() + "'...");

                setComposition(compositions.get(0));
            }
        } else {
            // We got a set loaded
            try {
                if (setService.getCurrentSet().getSetCompositionList().size() > 0) {
                    setCompositionName(setService.getCurrentSet().getSetCompositionList().get(0).getName());
                }
            } catch (Exception e) {
                logger.error("Could not read current composition", e);
            }
        }
    }

    @Override
    public synchronized void loadCompositionName(String compositionName) throws Exception {
        if (currentCompositionPlayer.getComposition() != null && compositionName.equals(currentCompositionPlayer.getComposition().getName())) {
            setCompositionName(compositionName);
        }

        currentCompositionPlayer.loadFiles();
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

        logger.debug("Wait for all devices to be loaded...");

        // Wait for the compositions on all devices to be loaded
        playExecutor.shutdown();

        while (!playExecutor.isTerminated()) {
            Thread.sleep(50);
        }

        // Load the local files outside the executor for better error handling
        currentCompositionPlayer.loadFiles();

        logger.debug("All devices loaded");

        if (currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.LOADED && currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.PAUSED) {
            // Maybe the composition stopped meanwhile
            return;
        }

        stopDefaultComposition();

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
        CompositionPlayer compositionPlayer = new CompositionPlayer(notificationService, activityNotificationMidiService, this, settingsService, midiRoutingService, capabilitiesService, operatingSystemInformationService);
        compositionPlayer.setSample(true);
        compositionPlayer.setComposition(composition);
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
    public synchronized void stop(boolean playDefaultComposition) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(30);

        // Reset the DMX universe to clear left out signals
        dmxService.reset();

        // Stop all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                executor.execute(() -> remoteDevice.stop(playDefaultComposition));
            }
        }

        // Also stop the local composition
        executor.execute(() -> {
            try {
                currentCompositionPlayer.stop();
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

        // Play the default composition, if necessary
        if (playDefaultComposition) {
            playDefaultComposition();
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        stop(true);
    }

    @Override
    public synchronized void seek(long positionMillis) throws Exception {
        if (currentCompositionPlayer != null) {
            currentCompositionPlayer.seek(positionMillis);
        }
    }

    private synchronized void playDefaultComposition() throws Exception {
        if (defaultCompositionPlayer.getComposition() != null) {
            // The default composition is already initialized
            return;
        }

        defaultCompositionPlayer = new CompositionPlayer(notificationService, activityNotificationMidiService, this, settingsService, midiRoutingService, capabilitiesService, operatingSystemInformationService);

        if (settingsService.getSettings().getDefaultComposition() == null || settingsService.getSettings().getDefaultComposition().length() == 0) {
            return;
        }

        logger.info("Play default composition");

        defaultCompositionPlayer.setDefaultComposition(true);
        defaultCompositionPlayer.setComposition(compositionService.getComposition(settingsService.getSettings().getDefaultComposition()));
        defaultCompositionPlayer.play();
    }

    private synchronized void stopDefaultComposition() throws Exception {
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

    @PreDestroy
    public void close() throws Exception {
        currentCompositionPlayer.stop();
        defaultCompositionPlayer.stop();

        for (CompositionPlayer sampleCompositionPlayer : sampleCompositionPlayerList) {
            sampleCompositionPlayer.stop();
        }
    }

    @Override
    public void setNextComposition() throws Exception {
        if (setService.getCurrentSet() == null) {
            if (setService.getNextSetComposition() != null) {
                stop(true);
                setCompositionName(setService.getNextSetComposition().getName());
            }
        } else {
            if (compositionService.getNextComposition(currentCompositionPlayer.getComposition()) != null) {
                stop(false);
                setComposition(compositionService.getNextComposition(currentCompositionPlayer.getComposition()));
            }
        }
    }

    @Override
    public void setPreviousComposition() throws Exception {
        // Rewind current composition instead of selecting the previous one
        if(currentCompositionPlayer.getPositionMillis() > 0) {
            stop(true);
        }

        if (setService.getCurrentSet() == null) {
            if (setService.getPreviousSetComposition() != null) {
                stop(true);
                setCompositionName(setService.getPreviousSetComposition().getName());
            }
        } else {
            if (compositionService.getPreviousComposition(currentCompositionPlayer.getComposition()) != null) {
                stop(true);
                setComposition(compositionService.getPreviousComposition(currentCompositionPlayer.getComposition()));
            }
        }
    }

    @Override
    public void compositionPlayerFinishedPlaying(CompositionPlayer compositionPlayer) throws Exception {
        if (compositionPlayer.isSample()) {
            sampleCompositionPlayerList.remove(compositionPlayer);
            return;
        }

        if (currentCompositionPlayer.getComposition().isAutoStartNextComposition() && setService.getNextSetComposition() != null) {
            // Stop the current composition, don't play the default composition but start
            // playing the next composition

            setNextComposition();
        } else if (sessionService.getSession().isAutoSelectNextComposition()) {
            // Stop the current composition, play the default composition and select the
            // next composition automatically (if there is one)

            stop(true);

            if (setService.getCurrentSet() != null) {
                // Set the next composition in the set
                if (setService.getNextSetComposition() != null) {
                    setService.setCurrentCompositionIndex(setService.getCurrentCompositionIndex() + 1);
                }
            } else {
                // Set the next composition without a set
                setComposition(compositionService.getNextComposition(currentCompositionPlayer.getComposition()));
            }
        } else {
            stop(true);
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
    }

    public void setComposition(Composition currentComposition) throws Exception {
        setComposition(currentComposition, true, false);
    }

    public void setCompositionName(String name) throws Exception {
        setComposition(compositionService.getComposition(name));
    }

}
