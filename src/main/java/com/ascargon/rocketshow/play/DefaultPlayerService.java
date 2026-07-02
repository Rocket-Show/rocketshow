package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.api.RemoteDevice;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.gstreamer.GstreamerInitService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.settings.DefaultCompositionChangedEvent;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manage play states, sets and compositions. The composition is read and played in the
 * CompositionPlayer.
 */
@Service
public class DefaultPlayerService implements PlayerService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultPlayerService.class);

    @Getter
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final CompositionService compositionService;
    @Getter
    private final SetService setService;
    private final SessionService sessionService;
    @Getter
    private final LightingService lightingService;
    @Getter
    private final DesignerService designerService;
    @Getter
    private final ActionExecutionService actionExecutionService;
    @Getter
    private final MidiTimecodeService midiTimecodeService;
    @Getter
    private final CapabilitiesService capabilitiesService;
    @Getter
    private final CompositionPipelineBuilder compositionPipelineBuilder;
    private final RaspberryGpioOutService raspberryGpioOutService;

    // Regular composition player
    private final CompositionPlayer currentCompositionPlayer;

    // The composition, which is being played in between other compositions (like a screensaver)
    private final CompositionPlayer defaultCompositionPlayer;

    // Used to play the composition from the editor to test it/try it out before saving
    private final CompositionPlayer testCompositionPlayer;

    private final List<CompositionPlayer> sampleCompositionPlayerList = new ArrayList<>();

    public DefaultPlayerService(
            NotificationService notificationService,
            SettingsService settingsService,
            CompositionService compositionService,
            SetService setService,
            SessionService sessionService,
            LightingService lightingService,
            DesignerService designerService,
            MidiTimecodeService midiTimecodeService,
            ActionExecutionService actionExecutionService,
            CapabilitiesService capabilitiesService,
            CompositionPipelineBuilder compositionPipelineBuilder,
            RaspberryGpioOutService raspberryGpioOutService,

            // import to make sure, Gstreamer is initialized before using it here
            GstreamerInitService gstreamerInitService
    ) {

        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.compositionService = compositionService;
        this.setService = setService;
        this.sessionService = sessionService;
        this.lightingService = lightingService;
        this.designerService = designerService;
        this.midiTimecodeService = midiTimecodeService;
        this.actionExecutionService = actionExecutionService;
        this.capabilitiesService = capabilitiesService;
        this.compositionPipelineBuilder = compositionPipelineBuilder;
        this.raspberryGpioOutService = raspberryGpioOutService;

        currentCompositionPlayer = new CompositionPlayer(this);
        defaultCompositionPlayer = new CompositionPlayer(this);
        defaultCompositionPlayer.setDefaultComposition(true);
        testCompositionPlayer = new CompositionPlayer(this);

        try {
            playDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not play default composition", e);
        }

        // Load the last set/composition
        try {
            if (sessionService.getSession() != null && sessionService.getSession().getCurrentSetName() != null && !sessionService.getSession().getCurrentSetName().isEmpty()) {
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
        if (!setName.isEmpty()) {
            setService.setCurrentSet(compositionService.getSet(setName));
        } else {
            setService.setCurrentSet(null);
        }

        // Read the current composition file
        if (setService.getCurrentSet() == null) {
            // We have no set. Simply read the first composition, if available
            logger.debug("Try setting an initial composition...");

            List<Composition> compositions = compositionService.getAllCompositions();

            if (!compositions.isEmpty()) {
                logger.debug("Set initial composition '" + compositions.get(0).getName() + "'...");

                setComposition(compositions.get(0));
            } else {
                setComposition(null);
            }
        } else {
            // We got a set loaded
            if (!setService.getCurrentSet().getSetCompositionList().isEmpty()) {
                setCompositionName(setService.getCurrentSet().getSetCompositionList().get(0).getName());
            } else {
                setComposition(null);
            }
        }

        setService.setCurrentCompositionIndex(0);

        // Persist the selected set in the session
        sessionService.getSession().setCurrentSetName(setName);
        sessionService.save();

        notificationService.notifyClients(this, setService);
    }

    @Override
    public synchronized void loadCompositionName(String compositionName) throws Exception {
        if (currentCompositionPlayer.getComposition() == null || !compositionName.equals(currentCompositionPlayer.getComposition().getName())) {
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

        if (!settingsService.getSettings().getRemoteDeviceList().isEmpty()) {
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
            if (!playExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.error("Timeout while waiting for the compositions to load on remote devices");
            }
        }

        // Load the local files outside the executor for better error handling
        currentCompositionPlayer.loadFiles();

        logger.debug("Files loaded");

        if (currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.LOADED
                && currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.PAUSED
        ) {
            // Maybe the composition stopped meanwhile
            return;
        }

        stopDefaultComposition();
        testCompositionPlayer.stop();

        logger.debug("Prepare playing on all devices...");

        // Play the composition on all remote devices
        for (RemoteDevice remoteDevice : settingsService.getSettings().getRemoteDeviceList()) {
            if (remoteDevice.isSynchronize()) {
                remoteDevice.play();
            }
        }

        // Play the composition locally
        currentCompositionPlayer.play();

        logger.debug("Playing on all devices...");
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
        CompositionPlayer compositionPlayer = new CompositionPlayer(this);
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
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            logger.error("Timeout while waiting for the compositions to stop on remote devices");
        }

        // Reset the lighting universe to clear left out signals
        lightingService.reset();

        // Set all configured external control GPIO output pins low (same as blacking out the lighting)
        raspberryGpioOutService.setAllLow();

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

        if (settingsService.getSettings().getDefaultComposition() == null || settingsService.getSettings().getDefaultComposition().isEmpty()) {
            return;
        }

        logger.info("Play default composition");

        defaultCompositionPlayer.setComposition(compositionService.getComposition(settingsService.getSettings().getDefaultComposition()));
        defaultCompositionPlayer.play();
    }

    @EventListener
    public synchronized void defaultCompositionChanged(DefaultCompositionChangedEvent event) {
        try {
            refreshDefaultComposition();
        } catch (Exception e) {
            logger.error("Could not refresh default composition", e);
        }
    }

    private synchronized void refreshDefaultComposition() throws Exception {
        stopDefaultComposition();

        if (currentCompositionPlayer.getPlayState() != CompositionPlayer.PlayState.STOPPED) {
            return;
        }

        playDefaultComposition();
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
            if (compositionService.getNextComposition(currentCompositionPlayer.getComposition()) != null) {
                setComposition(compositionService.getNextComposition(currentCompositionPlayer.getComposition()));
            }
        } else {
            if (setService.getNextSetComposition() != null) {
                setService.setCurrentCompositionIndex(setService.getCurrentCompositionIndex() + 1);
                setCompositionName(setService.getCurrentCompositionName());
            }
        }
    }

    @Override
    public void setPreviousComposition() throws Exception {
        // Rewind current composition instead of selecting the previous one
        if (currentCompositionPlayer.getPositionMillis() > 0) {
            stop(true);
        }

        if (setService.getCurrentSet() == null) {
            if (compositionService.getPreviousComposition(currentCompositionPlayer.getComposition()) != null) {
                stop(true);
                setComposition(compositionService.getPreviousComposition(currentCompositionPlayer.getComposition()));
            }
        } else {
            if (setService.getPreviousSetComposition() != null) {
                stop(true);
                setService.setCurrentCompositionIndex(setService.getCurrentCompositionIndex() - 1);
                setCompositionName(setService.getCurrentCompositionName());
            }
        }
    }

    @Override
    public void compositionPlayerFinishedPlaying(CompositionPlayer compositionPlayer) throws Exception {
        if (compositionPlayer.isSample()) {
            // Do nothing, if we played the composition as sample (multiple parallel possible)

            sampleCompositionPlayerList.remove(compositionPlayer);
            return;
        }

        if (sessionService.getSession().isAutoSelectNextComposition()
                && setService.getCurrentSet() != null
                && setService.getCurrentSet().getSetCompositionList() != null
                && setService.getCurrentSet().getSetCompositionList().size() > setService.getCurrentCompositionIndex()
                && setService.getCurrentSet().getSetCompositionList().get(setService.getCurrentCompositionIndex()).isAutoStartNextComposition()
                && setService.getNextSetComposition() != null) {

            // Stop the current composition, don't play the default composition but start
            // playing the next composition
            logger.info("Automatically start the next composition");
            stop(false);
            setNextComposition();
            play();
        } else if (sessionService.getSession().isAutoSelectNextComposition()) {
            // Stop the current composition, play the default composition and select the
            // next composition automatically (if there is one)

            stop(true);
            setNextComposition();
        } else {
            stop(true);
        }
    }

    public long getPositionMillis() {
        return currentCompositionPlayer.getPositionMillis();
    }

    public void setComposition(Composition composition, boolean playDefaultCompositionWhenStoppingComposition,
                               boolean forceLoad) throws Exception {

        if (composition != null && composition.getName().equals(this.getCompositionName()) && !forceLoad) {
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

    @Override
    public void playTestComposition(Composition composition) throws Exception {
        stop(false);
        testCompositionPlayer.stop();
        testCompositionPlayer.setComposition(composition);
        testCompositionPlayer.play();
    }

    @Override
    public void stopTestComposition() throws Exception {
        testCompositionPlayer.stop();
        playDefaultComposition();
    }

    @Override
    public CompositionPlayer getTestCompositionPlayer() {
        return testCompositionPlayer;
    }

}
