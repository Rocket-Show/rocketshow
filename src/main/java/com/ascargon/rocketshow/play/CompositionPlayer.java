package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.ActionTriggerComposition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.settings.CapabilitiesService;
import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.lighting.designer.DesignerService;
import com.ascargon.rocketshow.lighting.designer.Project;
import com.ascargon.rocketshow.midi.MidiTimecodeService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Manage the playing of a composition (play/pause/resume/stop/seek/loop, action triggers and
 * position tracking). The actual GStreamer pipeline is constructed by the
 * {@link CompositionPipelineBuilder} and driven through the resulting {@link CompositionPipeline}.
 */
@Service
public class CompositionPlayer implements CompositionPipelineBuilder.MasterEventListener {

    private final static Logger logger = LoggerFactory.getLogger(CompositionPlayer.class);

    private final String uuid = String.valueOf(UUID.randomUUID());

    public enum PlayState {
        PLAYING, // Is the composition playing?
        PAUSED, // Is the composition paused?
        STOPPING, // Is the composition being stopped?
        STOPPED, // Is the composition stopped?
        LOADING, // Is the composition waiting for all files to be loaded to start playing?
        LOADED // Has the composition finished loading all files?
    }

    private final NotificationService notificationService;
    private final PlayerService playerService;
    private final CapabilitiesService capabilitiesService;
    private final SetService setService;
    private final LightingService lightingService;
    private final DesignerService designerService;
    private final ActionExecutionService actionExecutionService;
    private final MidiTimecodeService midiTimecodeService;
    private final CompositionPipelineBuilder compositionPipelineBuilder;

    private Composition composition;
    private PlayState playState = PlayState.STOPPED;
    private boolean firstPlayDone = false; // Has the pipeline played at least once?
    // True once the master pipeline reached EOS but the composition is still running to let trailing
    // actions (placed after the longest source) fire. Position then advances on the wall clock.
    private boolean masterEosReached = false;
    private long startPosition = 0; // The position, the composition started the last play
    private long lastPlayTimeMillis; // The system time, when playing started
    private ScheduledFuture<?> autoStopHandle;

    // Is this the default composition?
    private boolean isDefaultComposition = false;

    // Is this composition played as a sample?
    private boolean isSample = false;

    // The constructed GStreamer pipeline (master + slaves). Null when nothing is loaded.
    private CompositionPipeline compositionPipeline;

    // Execute the action triggers at the specified positions
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduleActionTriggerHandle;
    private List<ScheduledFuture<?>> actionTriggerHandleList = new ArrayList<>();

    // Ensure, each trigger is executed only once (during inconsistencies when re-scheduling periodically)
    private List<ActionTriggerComposition> remainingActionTriggerCompositionList;

    public CompositionPlayer(DefaultPlayerService playerService) {
        this.playerService = playerService;
        this.notificationService = playerService.getNotificationService();
        this.capabilitiesService = playerService.getCapabilitiesService();
        this.setService = playerService.getSetService();
        this.lightingService = playerService.getLightingService();
        this.designerService = playerService.getDesignerService();
        this.actionExecutionService = playerService.getActionExecutionService();
        this.midiTimecodeService = playerService.getMidiTimecodeService();
        this.compositionPipelineBuilder = playerService.getCompositionPipelineBuilder();
    }

    // --- CompositionPipelineBuilder.MasterEventListener ---

    @Override
    public void onError(String message) {
        try {
            notificationService.notifyClients(message);
        } catch (Exception e) {
            logger.error("Could not notify clients about an error", e);
        }
        try {
            stop();
        } catch (Exception e) {
            logger.error("Could not stop compostion triggered by an error", e);
        }
    }

    @Override
    public void onPlaying() {
        firstPlayDone = true;
        playState = PlayState.PLAYING;

        if (!isDefaultComposition && !isSample) {
            try {
                notificationService.notifyClients(playerService, setService);
            } catch (Exception e) {
                logger.error("Could not notify clients about a playing event", e);
            }
        }
    }

    @Override
    public void onEndOfStream() {
        try {
            onMasterEndOfStream();
        } catch (Exception e) {
            logger.error("Could not handle the master end of stream", e);
        }
    }

    private void calculateRemainingActionTriggerList() {
        remainingActionTriggerCompositionList = new CopyOnWriteArrayList<>();
        remainingActionTriggerCompositionList.addAll(composition.getActionTriggerList().stream().filter(trigger -> trigger.getPositionMillis() >= getPositionMillis()).toList());
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        boolean hasActiveFile = false;
        firstPlayDone = false;

        if (playState != PlayState.STOPPED) {
            // A resume (PAUSED) returns here -> keep masterEosReached so a trailing-action tail survives
            return;
        }

        // Fresh load -> not in a trailing-action tail
        masterEosReached = false;

        // Search for active files
        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.isActive()) {
                hasActiveFile = true;
                break;
            }
        }

        if (!hasActiveFile
                && designerService.getProjectByCompositionName(composition.getName()) == null
                && composition.getActionTriggerList().isEmpty()
        ) {
            // No files to be played, no actions and no designer project (maybe a lead sheet)
            if (!isDefaultComposition && !isSample) {
                notificationService.notifyClients(playerService, setService);
            }

            return;
        }

        if (hasActiveFile && !capabilitiesService.getCapabilities().isGstreamer()) {
            throw new Exception("Gstreamer is required to play this composition but not available");
        }

        playState = PlayState.LOADING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.debug("Loading composition '" + composition.getName() + "...");

        // Destroy an old pipeline, if required
        stopPipeline();

        // Initialize lighting without designer
        lightingService.setExternalSync(false);

        // Destroy an old designer project, if required
        this.designerService.close();

        // Prepare the scheduler for all contained action triggers (and some buffer for overlapping
        // runs). Created before the pipeline so it can recreate H.265 slaves off the bus thread.
        scheduler = Executors.newScheduledThreadPool(composition.getActionTriggerList().size() + 5);

        if (hasActiveFile) {
            compositionPipeline = compositionPipelineBuilder.build(composition, isSample, this, scheduler);
        }

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            notificationService.notifyClients(playerService, setService);
        }
    }

    private void stopPipeline() {
        if (compositionPipeline != null) {
            compositionPipeline.stop();
            compositionPipeline = null;
        }
    }

    private void startAutoStopTimer() {
        // Re-entrant (also called on resume / each loop): drop any previously scheduled end first
        stopAutoStopTimer();

        if (compositionPipeline != null && compositionPipeline.hasMaster() && !masterEosReached) {
            // A live master pipeline drives the end via its EOS -> no need for the timer
            return;
        }

        // No master, or a master that already reached EOS (trailing-action tail) -> the wall clock
        // drives the end. Add a small buffer so the last action triggers fire before we loop/finish.
        autoStopHandle = scheduler.schedule(this::reachedEnd,
                composition.getDurationMillis() - getPositionMillis() + 50, MILLISECONDS);
    }

    private void stopAutoStopTimer() {
        if (autoStopHandle == null) {
            return;
        }

        // Don't interrupt the thread, because it might need to notify some websocket sessions
        autoStopHandle.cancel(false);
        autoStopHandle = null;
    }

    // The master pipeline reached its end (the longest non-looping source). If actions are placed
    // after it, run them out first on the wall clock (the "trailing-action tail") and only reach the
    // real end of the composition once the full duration has elapsed; otherwise the end is now.
    private void onMasterEndOfStream() {
        if (masterEosReached) {
            // Already in the tail (e.g. a duplicate EOS while resuming) -> nothing to do
            return;
        }

        long remainingMillis = composition.getDurationMillis() - getPositionMillis();

        if (remainingMillis <= 0 && composition.isLoop()) {
            // No trailing tail and looping -> loop straight away, so there is no gap between
            // iterations (an extra wait would be audible/visible).
            reachedEnd();
            return;
        }

        // Otherwise run out the wall-clock tail before reaching the end: any trailing actions
        // (remainingMillis), or just the small +50ms buffer so an action exactly at the end still
        // fires. Anchor wall-clock timing at the current position (the pipeline position is now
        // frozen at EOS) so it keeps advancing, survive pause/resume, and let the auto-stop timer
        // loop/finish once the duration fully elapses. The composition must NOT loop/finish here.
        if (remainingMillis > 0) {
            logger.info("Master pipeline reached EOS; waiting {}ms for trailing actions", remainingMillis);
        }
        startPosition = getPositionMillis();
        lastPlayTimeMillis = System.currentTimeMillis();
        masterEosReached = true;
        startAutoStopTimer();
    }

    // The composition reached its real end (full duration elapsed). Loop it back to the start if it
    // is set to loop, otherwise finish (media -> auto-next handling; pure actions -> just stop).
    private void reachedEnd() {
        if (composition.isLoop()) {
            loopToStart();
            return;
        }

        try {
            if (compositionPipeline != null && compositionPipeline.hasMedia()) {
                playerService.compositionPlayerFinishedPlaying(this);
            } else {
                stop();
            }
        } catch (Exception e) {
            logger.error("Could not finish the composition at its end", e);
        }
    }

    // Loop the whole composition back to the start (master if any + slaves + action triggers).
    private void loopToStart() {
        if (compositionPipeline != null && compositionPipeline.masterHasH265()) {
            // H.265 master can't seek -> recreate it. Off the bus/timer thread to avoid deadlocking
            // pipeline disposal.
            scheduler.execute(this::recreateMasterAndRestart);
            return;
        }

        try {
            // seek(0) restarts the master (if any), re-phases the slaves and re-arms the actions.
            seek(0);
            // Re-arm the end timer for the next cycle. With a master this is a no-op (its EOS drives
            // the next loop); without one the wall-clock timer drives it.
            startAutoStopTimer();
        } catch (Exception e) {
            logger.error("Could not loop the composition back to the start", e);
        }
    }

    private void recreateMasterAndRestart() {
        try {
            stopPipeline();
            firstPlayDone = false;
            masterEosReached = false;
            compositionPipeline = compositionPipelineBuilder.build(composition, isSample, this, scheduler);
            if (compositionPipeline.hasMaster()) {
                compositionPipeline.prerollMaster();
                compositionPipeline.detectMasterH265();
                compositionPipeline.playMaster();
            }
            compositionPipeline.startSlaves(0);
            lastPlayTimeMillis = System.currentTimeMillis();
            calculateRemainingActionTriggerList();
        } catch (Exception e) {
            logger.error("Could not recreate the H.265 pipeline to loop the composition", e);
        }
    }

    private void startActionTriggerTimer() {
        // Schedule all action triggers in the future of the current position

        // Cancel all current schedules
        for (ScheduledFuture<?> handle : actionTriggerHandleList) {
            handle.cancel(false);
        }

        // Create new schedules for each trigger
        long currentPositionMillis = getPositionMillis();
        for (ActionTriggerComposition actionTriggerComposition : remainingActionTriggerCompositionList) {
            final Runnable runnable = () -> {
                try {
                    actionExecutionService.executeFromTrigger(actionTriggerComposition);
                } catch (Exception e) {
                    logger.error("Could not execute actions triggered at position {}", actionTriggerComposition.getPositionMillis(), e);
                    try {
                        notificationService.notifyClients("Could not execute action: " + e.getCause());
                    } catch (Exception ex) {
                    }
                }
                remainingActionTriggerCompositionList.remove(actionTriggerComposition);
            };
            ScheduledFuture<?> handle = scheduler.schedule(runnable, actionTriggerComposition.getPositionMillis() - currentPositionMillis, MILLISECONDS);
            actionTriggerHandleList.add(handle);
        }
    }

    private void scheduleActionTriggerTimers() {
        // Schedule the timers to execute the actions

        if (composition.getActionTriggerList().isEmpty()) {
            // No actions to trigger
            return;
        }

        if (compositionPipeline == null || !compositionPipeline.hasMaster()) {
            // No master pipeline to stay in sync with -> only schedule once
            startActionTriggerTimer();
            return;
        }

        // Schedule periodically to stay in sync with the pipeline, which might drift under certain circumstances

        if (scheduleActionTriggerHandle != null) {
            // Timer already started
            return;
        }

        final Runnable runnable = this::startActionTriggerTimer;
        scheduleActionTriggerHandle = scheduler.scheduleAtFixedRate(runnable, 0, 2000, MILLISECONDS);
    }

    private void stopActionTriggerTimer() {
        if (scheduleActionTriggerHandle == null) {
            // No timer running currently
            return;
        }

        scheduleActionTriggerHandle.cancel(false);
        scheduleActionTriggerHandle = null;
    }

    // Cancel any action triggers that are scheduled but haven't fired yet, so they don't fire after
    // the composition is paused or stopped (e.g. leaking into a following composition).
    private void cancelPendingActionTriggers() {
        for (ScheduledFuture<?> handle : actionTriggerHandleList) {
            handle.cancel(false);
        }
        actionTriggerHandleList.clear();
    }

    public void play() throws Exception {
        if (composition == null) {
            return;
        }

        // Load the files, if not already done by a previously by a separate call
        loadFiles();

        // Load the designer files
        // -> no separate step, because there's only one global handler and the default composition is closed
        // after the loading step.
        Project designerProject = designerService.getProjectByCompositionName(composition.getName());
        if (designerProject != null) {
            logger.info("Designer project found. Load it...");
            designerService.load(this, designerProject, compositionPipeline == null ? null : compositionPipeline.getMasterPipeline());
        }

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (compositionPipeline != null && compositionPipeline.hasMaster()) {
            if (masterEosReached) {
                // Resuming during the trailing-action tail: the master is exhausted (at EOS) and its
                // position is past the end, so don't re-preroll or seek it. Just resume playback so
                // the slaves/clock keep running while the wall-clock timers (re-armed below) finish.
                compositionPipeline.playMaster();
                compositionPipeline.resumeSlaves();
            } else {
                // A fresh start prerolls and phase-aligns the slaves; a resume just continues them
                boolean freshStart = !firstPlayDone;

                if (freshStart) {
                    compositionPipeline.prerollMaster();

                    if (compositionPipeline.detectMasterH265()) {
                        logger.warn("Hardware H.265 decoder detected in the master pipeline — seeking will be disabled for this composition");
                    }

                    if (startPosition > 0 && !compositionPipeline.masterHasH265()) {
                        compositionPipeline.seekMasterTo(startPosition);
                    }
                }
                compositionPipeline.playMaster();

                // The master is now playing -> bring up the slaves on its clock
                if (freshStart) {
                    compositionPipeline.startSlaves(startPosition);
                } else {
                    compositionPipeline.resumeSlaves();
                }
            }
        }

        lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();

        startAutoStopTimer();
        scheduleActionTriggerTimers();

        designerService.play();
        startMidiTimecode();

        if (compositionPipeline == null || !compositionPipeline.hasMaster()) {
            // No master pipeline (every source loops, or only actions/designer) -> start any slaves
            // on the wall-clock timeline, then issue the playing state directly.
            if (compositionPipeline != null) {
                compositionPipeline.startSlaves(startPosition);
            }
            playState = PlayState.PLAYING;
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void pause() throws Exception {
        if (playState == PlayState.PAUSED) {
            return;
        }

        logger.info("Pausing composition '" + composition.getName() + "'");

        if (compositionPipeline != null && compositionPipeline.hasAnyH265Stream()) {
            logger.warn("Pause ignored: hardware H.265 decoder does not support pausing/resuming");
            return;
        }

        // Pause the composition (master + all slaves)
        if (compositionPipeline != null) {
            compositionPipeline.pause();
        }

        designerService.pause();

        startPosition = getPositionMillis();

        playState = PlayState.PAUSED;

        stopAutoStopTimer();
        stopActionTriggerTimer();
        cancelPendingActionTriggers();
        midiTimecodeService.stop(this);

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public void stop() throws Exception {
        startPosition = 0;
        firstPlayDone = false;
        masterEosReached = false;

        if (composition == null || playState == PlayState.STOPPED) {
            return;
        }

        playState = PlayState.STOPPING;

        if (!isDefaultComposition && !isSample) {
            notificationService.notifyClients(playerService, setService);
        }
        logger.info("Stopping composition '{}'", composition.getName());

        // Stop the composition (tears down the pipeline and closes its MIDI routers)
        stopPipeline();

        designerService.close();

        playState = PlayState.STOPPED;

        stopAutoStopTimer();
        stopActionTriggerTimer();
        cancelPendingActionTriggers();
        midiTimecodeService.stop(this);

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }

        logger.info("Composition '" + composition.getName() + "' stopped");
    }

    public void togglePlay() throws Exception {
        if (playState == PlayState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    public void seek(long positionMillis) throws Exception {
        if (compositionPipeline != null && compositionPipeline.masterHasH265()) {
            // Seeking is disabled for hardware H.265 decode on RPi. Two approaches were tried
            // and both fail:
            //   1. Early seek (before preroll): the hardware decoder ignores the seek event
            //      during pipeline initialisation and always prerolls at position 0.
            //   2. Post-preroll FLUSH seek: the decoder's sole DPB slot (MaxDpbPic = 1 for
            //      4K H.265 level 4.x) is held by the KMS scanout buffer. alloc_frame()
            //      cannot get a free slot and fails with "Not enough memory to decode H265
            //      stream". Unlike software decoders, the hardware path has no additional
            //      DPB slots to absorb the seek.
            logger.warn("Seek to {}ms ignored: hardware H.265 decoder does not support seeking", positionMillis);
            if (!isSample) {
                notificationService.notifyClients(playerService, setService);
            }
            return;
        }

        startPosition = positionMillis;

        // A (flushing) seek re-activates the pipeline, so we leave any trailing-action tail and let
        // the pipeline drive position and the end of stream again.
        masterEosReached = false;

        logger.debug("Seek to position " + positionMillis);

        // Seek the master (if any) and re-phase each slave to the in-loop position matching the new
        // position (e.g. seek to 35s with a 10s loop -> the slave jumps to 5s; H.265 slaves restart).
        if (compositionPipeline != null) {
            compositionPipeline.seek(positionMillis);
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();

        calculateRemainingActionTriggerList();
        scheduleActionTriggerTimers();
        if (playState == PlayState.PLAYING) {
            startMidiTimecode();
        }

        if (!isSample) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    private void startMidiTimecode() {
        if (isDefaultComposition || isSample) {
            return;
        }

        midiTimecodeService.start(this, this::getPositionMillis);
    }

    public long getPositionMillis() {
        if (composition == null) {
            return 0;
        }

        // If we're not playing, just return the current start position to resume playing
        if (playState != PlayState.PLAYING) {
            return startPosition;
        }

        // If there is a live pipeline -> use it (only if playing, querying its status is not reliably
        // otherwise). After the master reached EOS its position is frozen at the longest source, so
        // fall through to the wall clock to keep advancing through the trailing-action tail.
        if (compositionPipeline != null && compositionPipeline.hasMaster() && !masterEosReached) {
            long queriedMillis = compositionPipeline.queryMasterPositionMillis();
            if (queriedMillis >= startPosition) {
                // only return, if the query worked (shortly after seeking it's 0, even if we're playing)
                return queriedMillis;
            }
        }

        // No (usable) pipeline position (only actions, or the trailing-action tail) -> wall clock
        return System.currentTimeMillis() - lastPlayTimeMillis + startPosition;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof CompositionPlayer) {
            CompositionPlayer compositionPlayer = (CompositionPlayer) object;

            return this.uuid.equals(compositionPlayer.uuid);
        }

        return false;
    }

    public PlayState getPlayState() {
        return playState;
    }

    public void setPlayState(PlayState playState) {
        this.playState = playState;
    }

    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) throws Exception {
        this.composition = composition;

        if (!isSample && !isDefaultComposition) {
            notificationService.notifyClients(playerService, setService);
        }
    }

    public boolean isDefaultComposition() {
        return isDefaultComposition;
    }

    public void setDefaultComposition(boolean defaultComposition) {
        this.isDefaultComposition = defaultComposition;
    }

    public boolean isSample() {
        return isSample;
    }

    public void setSample(boolean sample) {
        isSample = sample;
    }

}
