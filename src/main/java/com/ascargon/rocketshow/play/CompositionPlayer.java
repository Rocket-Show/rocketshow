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

    // A drift up to this is not corrected at all, to keep the pipeline from being nudged constantly
    private final static long TIMECODE_DRIFT_TOLERANCE_MILLIS = 10;

    // Above this the media is not drifting but in the wrong place (the master looped, re-located or
    // the composition was started late) and is pulled back by seeking instead of by trimming
    private final static long TIMECODE_RESYNC_THRESHOLD_MILLIS = 100;

    // Don't resync by seeking more often than this, so a pipeline that cannot keep up (or a master
    // sending nonsense) does not turn into a seek storm
    private final static long TIMECODE_RESYNC_INTERVAL_MILLIS = 500;

    // The largest rate deviation used to trim drift. 0.2% is roughly 3.5 cents of pitch shift, which
    // is inaudible, and still far more correction authority than a sound card's drift (usually well
    // below 0.01%) ever needs.
    private final static double TIMECODE_MAX_RATE_DEVIATION = 0.002;

    // The error at which the full rate deviation is applied. Together with the clamp above this is a
    // proportional controller on a rate that integrates into a position, so it stays stable.
    private final static double TIMECODE_FULL_CORRECTION_ERROR_MILLIS = 100;

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
    private volatile PlayState playState = PlayState.STOPPED;

    // Held while the pipeline is replaced or stopped, e.g. an H.265 composition starting again while it's stopped
    private final Object pipelineLock = new Object();
    private boolean firstPlayDone = false; // Has the pipeline played at least once?
    // True once the master pipeline reached EOS but the composition is still running to let trailing
    // actions or designer lights (placed after the longest source) play. Position then advances on the wall clock.
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

    // The position within this composition dictated by an external MIDI timecode master, or -1 when
    // this player does not follow a timecode. While it is set, it - not the pipeline - is this
    // composition's position, so the lighting designer, the action triggers and the web app follow
    // the master immediately and without drift. The media pipeline cannot simply be told a position
    // (it is clocked by the sound card and the display), so it is pulled towards the timecode
    // separately in syncToTimecode().
    private volatile long externalTimecodePositionMillis = -1;

    private long lastTimecodeResyncTimeMillis = 0;

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
        notifyError(message);
        try {
            stop();
        } catch (Exception e) {
            logger.error("Could not stop compostion triggered by an error", e);
        }
    }

    @Override
    public void onSlaveError(String message) {
        notifyError(message);
    }

    private void notifyError(String message) {
        try {
            notificationService.notifyClients(message);
        } catch (Exception e) {
            logger.error("Could not notify clients about an error", e);
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

    // Build the list of triggers still ahead of us. The anchor must be the intended start position
    // (0 on a fresh play/loop, the seek target on a seek), NOT the live getPositionMillis(): the
    // pipeline starts advancing asynchronously (onPlaying() flips playState to PLAYING from the bus
    // thread), so by the time this runs the live position can already be a few ms > 0. That would
    // drop a trigger at position 0 (0 >= 3 is false) before it is ever scheduled, so it never fires.
    private void calculateRemainingActionTriggerList(long fromMillis) {
        remainingActionTriggerCompositionList = new CopyOnWriteArrayList<>();
        remainingActionTriggerCompositionList.addAll(composition.getActionTriggerList().stream().filter(trigger -> trigger.getPositionMillis() >= fromMillis).toList());
    }

    // Load all files and construct the complete GST pipeline
    public void loadFiles() throws Exception {
        boolean hasActiveFile = false;
        firstPlayDone = false;

        if (playState != PlayState.STOPPED) {
            // A resume (PAUSED) returns here -> keep masterEosReached so a trailing tail survives
            return;
        }

        // Fresh load -> not in a trailing tail
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

        // Initialize lighting without designer. The scenes picked by hand on the Play screen keep playing until a
        // project of a composition takes the lights over, so their engine keeps sending the universes.
        if (!designerService.isPlayingManualScenes()) {
            lightingService.setExternalSync(false);
        }

        // Destroy an old designer project, if required
        this.designerService.close();

        // Prepare the scheduler for all contained action triggers (and some buffer for overlapping
        // runs). Created before the pipeline so it can recreate H.265 slaves off the bus thread.
        shutdownScheduler();
        scheduler = Executors.newScheduledThreadPool(composition.getActionTriggerList().size() + 5);

        if (hasActiveFile) {
            compositionPipeline = compositionPipelineBuilder.build(composition, designerService.getCompositionEndMillis(composition.getName()), isSample, this, scheduler);
        }

        logger.debug("Composition '" + composition.getName() + "' loaded");

        // Maybe we are stopping meanwhile
        if (playState == PlayState.LOADING && !isDefaultComposition && !isSample) {
            playState = PlayState.LOADED;
            notificationService.notifyClients(playerService, setService);
        }
    }

    // End the threads of the scheduler. Every load creates a new one, so they would pile up otherwise. A task
    // running at the moment (e.g. the end of the composition stopping it) still finishes, the pending ones don't run.
    private void shutdownScheduler() {
        if (scheduler == null) {
            return;
        }

        if (scheduler instanceof ScheduledThreadPoolExecutor scheduledThreadPoolExecutor) {
            scheduledThreadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            scheduledThreadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }

        scheduler.shutdown();
    }

    private void stopPipeline() {
        synchronized (pipelineLock) {
            if (compositionPipeline != null) {
                compositionPipeline.stop();
                compositionPipeline = null;
            }
        }
    }

    private void startAutoStopTimer() {
        // Re-entrant (also called on resume / each loop): drop any previously scheduled end first
        stopAutoStopTimer();

        if (scheduler == null) {
            // Nothing was ever loaded, so there is nothing to stop
            return;
        }

        if (isFollowingTimecode()) {
            // The timecode master drives the end of the composition
            return;
        }

        if (compositionPipeline != null && compositionPipeline.hasMaster() && !masterEosReached) {
            // A live master pipeline drives the end via its EOS -> no need for the timer
            return;
        }

        // No master, or a master that already reached EOS (trailing tail) -> the wall clock
        // drives the end. Add a small buffer so the last action triggers fire before we loop/finish.
        autoStopHandle = scheduler.schedule(this::reachedEnd,
                getDurationMillis() - getPositionMillis() + 50, MILLISECONDS);
    }

    private void stopAutoStopTimer() {
        if (autoStopHandle == null) {
            return;
        }

        // Don't interrupt the thread, because it might need to notify some websocket sessions
        autoStopHandle.cancel(false);
        autoStopHandle = null;
    }

    // The master pipeline reached its end (the longest non-looping source). If actions or designer
    // lights are placed after it, run them out first on the wall clock (the "trailing tail") and only
    // reach the real end of the composition once the full duration has elapsed; otherwise the end is now.
    private void onMasterEndOfStream() {
        if (isFollowingTimecode()) {
            // The position comes from the timecode master, so there is no wall-clock tail to run out
            // and nothing here has to anchor the end of the composition
            return;
        }

        if (masterEosReached) {
            // Already in the tail (e.g. a duplicate EOS while resuming) -> nothing to do
            return;
        }

        long remainingMillis = getDurationMillis() - getPositionMillis();

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
        if (isFollowingTimecode()) {
            // The timecode master decides when this composition ends, loops or is replaced by the
            // next one - never the local timeline
            return;
        }

        if (composition.isLoop()) {
            loopToStart();
            return;
        }

        try {
            // The player service decides what follows, also for a composition without files (e.g. only lighting): the
            // next composition of a set, or just stopping (e.g. a sample)
            playerService.compositionPlayerFinishedPlaying(this);
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
        synchronized (pipelineLock) {
            // Stopped meanwhile: nothing plays on
            if (playState != PlayState.PLAYING) {
                return;
            }

            try {
                stopPipeline();
                firstPlayDone = false;
                masterEosReached = false;
                compositionPipeline = compositionPipelineBuilder.build(composition, designerService.getCompositionEndMillis(composition.getName()), isSample, this, scheduler);
                if (compositionPipeline.hasMaster()) {
                    compositionPipeline.prerollMaster();
                    compositionPipeline.detectMasterH265();
                    compositionPipeline.playMaster();
                }
                compositionPipeline.startSlaves(0);
                lastPlayTimeMillis = System.currentTimeMillis();
                calculateRemainingActionTriggerList(0);
            } catch (Exception e) {
                logger.error("Could not recreate the H.265 pipeline to loop the composition", e);
            }
        }
    }

    private void startActionTriggerTimer() {
        // Schedule all action triggers in the future of the current position

        if (scheduler == null) {
            // Nothing was ever loaded, so there is nothing to trigger against
            return;
        }

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

        if (composition.getActionTriggerList().isEmpty() || scheduler == null) {
            // No actions to trigger, or nothing loaded to trigger against
            return;
        }

        if (!isFollowingTimecode() && (compositionPipeline == null || !compositionPipeline.hasMaster())) {
            // No master pipeline and no timecode master to stay in sync with -> only schedule once
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

        if (playState == PlayState.STOPPED) {
            // Nothing to play (no media, no actions, no designer project)
            return;
        }

        // Load the designer files
        // -> no separate step, because there's only one global handler and the default composition is closed
        // after the loading step.
        Project designerProject = designerService.getProjectByCompositionName(composition.getName());
        if (designerProject != null && isDefaultComposition && designerService.hasManualScenes()) {
            // The default composition only plays in between: the scenes picked by hand on the Play screen go before
            // its lights
            designerProject = null;
        }
        if (designerProject != null) {
            logger.info("Designer project found. Load it...");
            designerService.load(this, designerProject, compositionPipeline == null ? null : compositionPipeline.getMasterPipeline());
        }

        // All files are loaded -> play the composition (start each file)
        logger.info("Playing composition '" + composition.getName() + "'...");

        if (compositionPipeline != null && compositionPipeline.hasMaster()) {
            if (masterEosReached) {
                // Resuming during the trailing tail: the master is exhausted (at EOS) and its
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

        calculateRemainingActionTriggerList(startPosition);

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
        if (playState == PlayState.PAUSED || playState == PlayState.STOPPED || playState == PlayState.STOPPING) {
            // Nothing plays. Pausing would skip loading the files on the next play.
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
        shutdownScheduler();
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

        // A (flushing) seek re-activates the pipeline, so we leave any trailing tail and let
        // the pipeline drive position and the end of stream again.
        masterEosReached = false;

        logger.debug("Seek to position " + positionMillis);

        // Seek the master (if any) and re-phase each slave to the in-loop position matching the new
        // position (e.g. seek to 35s with a 10s loop -> the slave jumps to 5s; H.265 slaves restart).
        if (compositionPipeline != null) {
            compositionPipeline.seek(positionMillis);
        }

        this.lastPlayTimeMillis = System.currentTimeMillis();

        if (playState == PlayState.PLAYING) {
            calculateRemainingActionTriggerList(startPosition);
            scheduleActionTriggerTimers();

            // Without a master pipeline, the end moves with the position
            startAutoStopTimer();

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

    // --- MIDI timecode slave ---

    /**
     * Hand this player the position an external MIDI timecode master is at, expressed as a position
     * inside this composition. Pass -1 to stop following a timecode.
     * <p>
     * This only publishes the position; use {@link #syncToTimecode(long)} to also pull the media
     * pipeline towards it.
     */
    public void setExternalTimecodePositionMillis(long positionMillis) {
        if (positionMillis < 0 && externalTimecodePositionMillis >= 0 && compositionPipeline != null) {
            // Stopped following -> hand the pipeline back at its normal rate
            compositionPipeline.setPlaybackRate(1);
        }

        externalTimecodePositionMillis = positionMillis;
    }

    public boolean isFollowingTimecode() {
        return externalTimecodePositionMillis >= 0;
    }

    /**
     * Follow the external MIDI timecode master, which is at the given position inside this
     * composition.
     * <p>
     * Everything that is recalculated from the position (the lighting designer) or dispatched by it
     * (the action triggers) follows the master exactly just by being handed the position. Audio and
     * video cannot: they are clocked by the sound card and the display, so they have to be pulled
     * towards the master instead. That is done in three bands:
     * <ul>
     *     <li>below {@link #TIMECODE_DRIFT_TOLERANCE_MILLIS} nothing is done, so the pipeline is not
     *     nudged constantly;</li>
     *     <li>up to {@link #TIMECODE_RESYNC_THRESHOLD_MILLIS} the playback rate is trimmed by a
     *     fraction of a per cent, which is inaudible and invisible and eases the error out over a
     *     few seconds - this is what absorbs the continuous drift between the sound card's clock and
     *     the master's;</li>
     *     <li>anything larger is not drift but a wrong position (the master looped or re-located, or
     *     the composition started late) and is corrected by seeking, which is audible but rare.</li>
     * </ul>
     * <p>
     * A pipeline that cannot trim its rate (see {@link CompositionPipeline#setPlaybackRate(double)})
     * falls back to the third band alone.
     */
    public void syncToTimecode(long targetPositionMillis) throws Exception {
        externalTimecodePositionMillis = targetPositionMillis;

        if (playState != PlayState.PLAYING || compositionPipeline == null || !compositionPipeline.hasMaster()) {
            // Nothing that is clocked by hardware -> handing over the position above is all it takes
            return;
        }

        long mediaPositionMillis = compositionPipeline.queryMasterPositionMillis();

        if (mediaPositionMillis < 0) {
            // The pipeline could not be queried (e.g. right after a seek)
            return;
        }

        // Positive: the media is ahead of the master and has to be slowed down
        long errorMillis = mediaPositionMillis - targetPositionMillis;

        if (Math.abs(errorMillis) <= TIMECODE_DRIFT_TOLERANCE_MILLIS) {
            compositionPipeline.setPlaybackRate(1);
            return;
        }

        if (Math.abs(errorMillis) <= TIMECODE_RESYNC_THRESHOLD_MILLIS) {
            // Trim it out. If the pipeline cannot change its rate on the fly the drift simply keeps
            // growing until it crosses the threshold below and is seeked away, which is far better
            // than seeking on every small deviation.
            compositionPipeline.setPlaybackRate(getTimecodeCorrectionRate(errorMillis));
            return;
        }

        // Too far off to trim out
        resyncToTimecode(targetPositionMillis, errorMillis);
    }

    private void resyncToTimecode(long targetPositionMillis, long errorMillis) throws Exception {
        if (compositionPipeline.masterHasH265()) {
            // Seeking is impossible with the hardware H.265 decoder (see seek()), so this
            // composition can only be started in sync, never pulled back into sync
            return;
        }

        long nowMillis = System.currentTimeMillis();

        if (nowMillis - lastTimecodeResyncTimeMillis < TIMECODE_RESYNC_INTERVAL_MILLIS) {
            // A seek takes a moment to settle; don't chase the master with a seek storm meanwhile
            return;
        }

        lastTimecodeResyncTimeMillis = nowMillis;

        logger.debug("Media is {}ms off the MIDI timecode; seeking to {}ms", errorMillis, targetPositionMillis);

        seek(targetPositionMillis);
    }

    private static double getTimecodeCorrectionRate(long errorMillis) {
        double correction = -errorMillis / TIMECODE_FULL_CORRECTION_ERROR_MILLIS * TIMECODE_MAX_RATE_DEVIATION;

        return 1 + Math.max(-TIMECODE_MAX_RATE_DEVIATION, Math.min(TIMECODE_MAX_RATE_DEVIATION, correction));
    }

    public long getPositionMillis() {
        if (composition == null) {
            return 0;
        }

        long timecodePositionMillis = externalTimecodePositionMillis;

        if (timecodePositionMillis >= 0) {
            // Following an external MIDI timecode master: it dictates the position (also while
            // paused, so the transport shows where the master is parked)
            return timecodePositionMillis;
        }

        // If we're not playing, just return the current start position to resume playing
        if (playState != PlayState.PLAYING) {
            return startPosition;
        }

        // If there is a live pipeline -> use it (only if playing, querying its status is not reliably
        // otherwise). After the master reached EOS its position is frozen at the longest source, so
        // fall through to the wall clock to keep advancing through the trailing tail.
        if (compositionPipeline != null && compositionPipeline.hasMaster() && !masterEosReached) {
            long queriedMillis = compositionPipeline.queryMasterPositionMillis();
            if (queriedMillis >= startPosition) {
                // only return, if the query worked (shortly after seeking it's 0, even if we're playing)
                return queriedMillis;
            }
        }

        // No (usable) pipeline position (only actions or lights, or the trailing tail) -> wall clock
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

    // How long the composition plays: as long as its files and actions, or as the lights of its designer project, whichever
    // is longer. A composition with nothing but lights plays as long as them.
    public long getDurationMillis() {
        if (composition == null) {
            return 0;
        }

        return Math.max(composition.getDurationMillis(), designerService.getCompositionEndMillis(composition.getName()));
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
