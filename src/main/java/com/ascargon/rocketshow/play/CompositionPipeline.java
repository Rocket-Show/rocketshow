package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.gstreamer.GstPipelineErrors;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiMessageParser;
import com.ascargon.rocketshow.midi.MidiRouter;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.freedesktop.gstreamer.event.SeekType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * The constructed GStreamer pipeline for a composition: an optional master pipeline (used to keep
 * all aligned sources in sync and to drive the timeline) plus one slave pipeline per looping source
 * that runs on its own loop while sharing the master clock.
 * <p>
 * Built by {@link CompositionPipelineBuilder}. The {@link CompositionPlayer} drives playback through
 * the control methods exposed here, so it never has to touch GStreamer construction itself.
 */
class CompositionPipeline {

    private final static Logger logger = LoggerFactory.getLogger(CompositionPipeline.class);

    // GST_SEEK_FLAG_INSTANT_RATE_CHANGE. Available since GStreamer 1.18 but missing from the Java
    // bindings' SeekFlags, so it has to be passed as a raw flag through GstApi.
    private final static int SEEK_FLAG_INSTANT_RATE_CHANGE = 1 << 11;

    private final CompositionPipelineBuilder builder;
    private final Composition composition;
    private final CompositionPipelineBuilder.MasterEventListener listener;

    // The master gstreamer pipeline, used to sync all files which must stay aligned (the longest
    // source plus all non-looping sources). Drives the composition timeline. May be null when every
    // source loops (then the wall clock drives the timeline and only slaves play).
    private Pipeline masterPipeline;

    // Slave pipelines: one per looping source that is shorter than the longest source. They run
    // independently (looping on their own) but share the master clock so they don't drift.
    private final List<SlavePipeline> slavePipelineList = new ArrayList<>();

    private final List<Element> volumeList = new ArrayList<>();

    // All MIDI routers used by this pipeline (master or slaves), closed on teardown
    private final List<MidiRouter> midiRouterList = new ArrayList<>();

    // A MIDI message parser and mapping shared by all MIDI files inside this composition
    private final MidiMessageParser midiMessageParser;
    private final MidiMapping midiMapping;

    // Used to recreate H.265 slave pipelines off the bus-callback thread (the player's scheduler)
    private final Executor recreateExecutor;

    // The composition file of each element directly in the master pipeline, by its name, to tell which file failed
    private final Map<String, String> fileNameByElement = new ConcurrentHashMap<>();

    // Why the stream of an element directly in a pipeline (e.g. a uridecodebin) could not be linked, by its name.
    // GStreamer only tells afterwards that nothing took the stream ("reason not-linked (-1)").
    private final Map<String, String> linkFailureByElement = new ConcurrentHashMap<>();

    // True when a hardware H.265 decoder is in use in the master pipeline
    private boolean masterHasH265 = false;

    // The playback rate currently applied to the master and its slaves. Only ever slightly off 1
    // (see setPlaybackRate), used to trim drift against an external timecode.
    private double playbackRate = 1;

    // Turns false as soon as an instant rate change is rejected (GStreamer older than 1.18, or an
    // element in the pipeline that does not handle it), so we stop trying.
    private boolean instantRateChangeSupported = true;

    // Set when stopped: a slave starting again (H.265) doesn't build a new pipeline afterwards
    private volatile boolean stopped = false;

    // Held while a slave is built again (H.265) and while stopping, so they never use the same pipeline at once
    private final Object rebuildLock = new Object();

    CompositionPipeline(CompositionPipelineBuilder builder, Composition composition, Pipeline masterPipeline,
                        CompositionPipelineBuilder.MasterEventListener listener, Executor recreateExecutor,
                        MidiMapping midiMapping, MidiMessageParser midiMessageParser) {
        this.builder = builder;
        this.composition = composition;
        this.masterPipeline = masterPipeline;
        this.listener = listener;
        this.recreateExecutor = recreateExecutor;
        this.midiMapping = midiMapping;
        this.midiMessageParser = midiMessageParser;
    }

    Composition getComposition() {
        return composition;
    }

    Pipeline getMasterPipeline() {
        return masterPipeline;
    }

    boolean hasMaster() {
        return masterPipeline != null;
    }

    // True if this pipeline actually plays any media (a master pipeline or at least one slave). May
    // be false even after a build when every source was skipped (no audio device / no HDMI).
    boolean hasMedia() {
        return masterPipeline != null || !slavePipelineList.isEmpty();
    }

    List<Element> getVolumeList() {
        return volumeList;
    }

    List<MidiRouter> getMidiRouterList() {
        return midiRouterList;
    }

    MidiMessageParser getMidiMessageParser() {
        return midiMessageParser;
    }

    MidiMapping getMidiMapping() {
        return midiMapping;
    }

    void addFileElement(String elementName, String fileName) {
        fileNameByElement.put(elementName, fileName);
    }

    // Link a stream of an element directly in a pipeline (e.g. a uridecodebin) as soon as it appeared
    void link(Element element, Pad pad, Pad sink) {
        String failure = GstPipelineErrors.link(pad, sink);
        if (failure != null) {
            linkFailureByElement.put(element.getName(), failure);
        }
    }

    // The error of a pipeline for the web app, with the file which failed (if known) and why its stream could not be
    // linked (if it could not), e.g. "song.wav: wavparse0: Internal data stream error. (streaming stopped, reason
    // not-linked (-1))\nCould not link audiouridecodebin0:src_0 -> audioconvert0:sink. Offered: ... Accepted: ..."
    String describeError(String topLevelElement, String description, String fileName) {
        String linkFailure = null;

        if (topLevelElement != null) {
            if (fileName == null) {
                fileName = fileNameByElement.get(topLevelElement);
            }
            linkFailure = linkFailureByElement.get(topLevelElement);
        }

        return (fileName == null ? "" : fileName + ": ") + description + (linkFailure == null ? "" : "\n" + linkFailure);
    }

    // Build a slave pipeline for the given looping source and add it to this pipeline
    void addSlave(CompositionFile compositionFile, int index) throws Exception {
        SlavePipeline slavePipeline = new SlavePipeline(compositionFile, index);
        slavePipeline.build();
        slavePipelineList.add(slavePipeline);
    }

    // TODO also allow to set the master volume in the interface
    void setMasterVolume(double volume) {
        logger.debug("Set the master volume to " + volume);
        for (Element volumeElement : volumeList) {
            volumeElement.set("volume", volume);
        }
    }

    // Bring the master pipeline to PAUSED (preroll) and wait for the state change
    void prerollMaster() {
        masterPipeline.setState(State.PAUSED);
        masterPipeline.getState(5, TimeUnit.SECONDS);
    }

    // Bring the master pipeline to PLAYING and wait for the state change
    void playMaster() {
        masterPipeline.setState(State.PLAYING);
        masterPipeline.getState(5, TimeUnit.SECONDS);
    }

    // Flushing seek of the master pipeline to the given position
    void seekMasterTo(long positionMillis) {
        masterPipeline.seekSimple(Format.TIME,
                EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT),
                positionMillis * 1_000_000L);
        masterPipeline.getState(5, TimeUnit.SECONDS);

        // A flushing seek starts a new segment at rate 1
        playbackRate = 1;
    }

    /**
     * Trim the playback rate of the master and its slaves, to pull the pipeline back into sync with
     * an external timecode without the audible/visible interruption of a seek.
     * <p>
     * Only ever called with a rate very close to 1 (see
     * {@link CompositionPlayer#syncToTimecode(long)}): a rate change shifts the pitch of the audio,
     * because there is no {@code scaletempo} in these pipelines, and it is only inaudible while the
     * deviation stays in the per mille range.
     * <p>
     * Uses an instant rate change, which applies to the buffers already queued in the sinks instead
     * of only taking effect at the next segment, and unlike a normal rate change does not need to
     * flush.
     * <p>
     * Best-effort: a pipeline that cannot change its rate on the fly (GStreamer older than 1.18, or
     * an element that does not handle it) simply keeps running at rate 1, and its drift is corrected
     * by seeking once it grows past the resync threshold.
     */
    void setPlaybackRate(double rate) {
        if (!instantRateChangeSupported || masterPipeline == null) {
            return;
        }

        if (Math.abs(rate - playbackRate) < 0.000001) {
            return;
        }

        if (!applyPlaybackRate(masterPipeline, rate)) {
            logger.info("The pipeline does not support instant rate changes; correcting the timecode drift by seeking only");
            instantRateChangeSupported = false;
            return;
        }

        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.setPlaybackRate(rate);
        }

        playbackRate = rate;
    }

    private static boolean applyPlaybackRate(Pipeline pipeline, double rate) {
        // An instant rate change must not carry a start/stop position, only the new rate
        return GstApi.GST_API.gst_element_seek(pipeline, rate, Format.TIME.intValue(),
                SEEK_FLAG_INSTANT_RATE_CHANGE,
                SeekType.NONE.intValue(), 0,
                SeekType.NONE.intValue(), 0);
    }

    long queryMasterPositionMillis() {
        return masterPipeline.queryPosition(TimeUnit.MILLISECONDS);
    }

    // Pause the whole pipeline (master + all slaves)
    void pause() {
        if (masterPipeline != null) {
            masterPipeline.pause();
        }
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.pause();
        }
    }

    // Seek the master (if any) and re-phase each slave to the matching in-loop position
    void seek(long positionMillis) {
        playbackRate = 1;

        if (masterPipeline != null) {
            seekMasterTo(positionMillis);
        }
        rephaseSlaves(positionMillis);
    }

    // Start all slave pipelines, locked onto the (already playing) master's clock and phase-aligned
    // to the given master position
    void startSlaves(long masterPositionMillis) {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.startSyncedToMaster(masterPositionMillis);
        }
    }

    // Resume all slaves from pause (the shared clock kept them aligned while paused)
    void resumeSlaves() {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.resume();
        }
    }

    // Re-phase all slaves to the master's new position (used when the master is looped or seeked)
    void rephaseSlaves(long masterPositionMillis) {
        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.rephaseTo(masterPositionMillis);
        }
    }

    // Detect whether a hardware H.265 decoder ended up in the master pipeline (only meaningful after
    // the master has been prerolled). Stores and returns the result.
    boolean detectMasterH265() {
        masterHasH265 = masterPipeline != null && findHardwareH265Decoder(masterPipeline);
        return masterHasH265;
    }

    boolean masterHasH265() {
        return masterHasH265;
    }

    // True if a hardware H.265 decoder is in use anywhere (master or any slave). H.265 cannot
    // seek or pause/resume on the Pi, so these operations are restricted when it is present.
    boolean hasAnyH265Stream() {
        if (masterHasH265) {
            return true;
        }
        for (SlavePipeline slavePipeline : slavePipelineList) {
            if (slavePipeline.hasH265()) {
                return true;
            }
        }
        return false;
    }

    // Avoid audio artifacts: mute the master and all slaves, then tear everything down (master,
    // slaves and MIDI routers).
    void stop() {
        stopped = true;

        synchronized (rebuildLock) {
            stopPipelines();
        }
    }

    private void stopPipelines() {
        if (masterPipeline != null || !slavePipelineList.isEmpty()) {
            setMasterVolume(0.0);
            for (SlavePipeline slavePipeline : slavePipelineList) {
                slavePipeline.mute();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        for (SlavePipeline slavePipeline : slavePipelineList) {
            slavePipeline.stop();
        }
        slavePipelineList.clear();

        if (masterPipeline != null) {
            masterPipeline.stop();
            masterPipeline.dispose();
            masterPipeline = null;
        }
        volumeList.clear();

        // Close all MIDI routers
        for (MidiRouter midiRouter : midiRouterList) {
            midiRouter.close();
        }
        midiRouterList.clear();
    }

    static boolean findHardwareH265Decoder(Bin bin) {
        for (Element element : bin.getElements()) {
            ElementFactory factory = element.getFactory();
            if (factory != null) {
                String name = factory.getName();
                if ((name.contains("h265") || name.contains("hevc")) && !name.startsWith("avdec")) {
                    return true;
                }
            }
            if (element instanceof Bin && findHardwareH265Decoder((Bin) element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A slave pipeline plays a single looping source that is shorter than the longest source.
     * It runs independently (looping on its own EOS) but shares the master's clock and base time
     * so it does not drift. The master applies its play/pause/stop/seek state to all slaves.
     */
    private class SlavePipeline {

        private final CompositionFile compositionFile;
        private final int index;
        private Pipeline slavePipeline;
        private final List<Element> volumeList = new ArrayList<>();
        private boolean hasH265 = false;

        // The trimmed rate the master currently runs at, re-applied after every own seek/restart
        // (which starts a new segment back at rate 1)
        private double slavePlaybackRate = 1;

        SlavePipeline(CompositionFile compositionFile, int index) {
            this.compositionFile = compositionFile;
            this.index = index;
        }

        // Construct the single-source pipeline with its own output (sink). Does not start it.
        void build() throws Exception {
            slavePipeline = new Pipeline("slave" + index);

            Bus bus = GstApi.GST_API.gst_element_get_bus(slavePipeline);
            GstPipelineErrors.onError(bus, (topLevelElement, description) -> {
                String error = describeError(topLevelElement, description, compositionFile.getName());
                logger.error("GST slave error: " + error);
                if (!stopped) {
                    listener.onSlaveError(error);
                }
            });
            // When the slave reaches the end of its source, loop it back to the start
            bus.connect((Bus.EOS) source -> loop());
            GstApi.GST_API.gst_object_unref(bus);

            // Audio slaves need their own output chain (mixer -> ... -> sink) to their device
            Map<AudioDevice, Element> audioMixerList = new HashMap<>();
            if (compositionFile instanceof AudioCompositionFile) {
                AudioDevice audioDevice = builder.getAudioDeviceList(List.of(compositionFile)).get(0);
                Element mixer = builder.buildAudioOutputChain(slavePipeline, audioDevice, false, false, volumeList);
                audioMixerList.put(audioDevice, mixer);
            }

            // hasVideo is true here: video slaves are only built when an HDMI device is connected
            builder.addCompositionFileToPipeline(compositionFile, slavePipeline, audioMixerList, index, true, CompositionPipeline.this);
        }

        // Preroll, lock onto the master clock, seek to the matching in-loop position, then play.
        void startSyncedToMaster(long masterPositionMillis) {
            if (slavePipeline == null) {
                return;
            }

            slavePipeline.setState(State.PAUSED);
            slavePipeline.getState(5, TimeUnit.SECONDS);

            hasH265 = findHardwareH265Decoder(slavePipeline);

            // Share the master clock so the slave never rate-drifts. We deliberately do NOT share
            // the base time: the slave runs its own (looping) timeline at a different phase, and a
            // shared base time would make every buffer arrive late and be dropped to "catch up".
            Clock masterClock = masterPipeline == null ? null : masterPipeline.getClock();
            if (masterClock != null) {
                slavePipeline.useClock(masterClock);
            }

            // Phase-align the loop to the master's position, then let the shared clock hold it.
            long phaseMillis = phasePositionMillis(masterPositionMillis);
            if (phaseMillis > 0 && !hasH265) {
                seekSlaveTo(phaseMillis);
                slavePipeline.getState(5, TimeUnit.SECONDS);
            }

            slavePipeline.setState(State.PLAYING);
            slavePipeline.getState(5, TimeUnit.SECONDS);

            applySlavePlaybackRate();
        }

        // The in-loop content position (ms) the slave should be at for the given master position,
        // i.e. where it would be if it had looped continuously since the composition start. E.g.
        // master at 35s with a 10s loop -> 5s. Offset shifts the loop's start on the timeline.
        private long phasePositionMillis(long masterPositionMillis) {
            long length = compositionFile.getDurationMillis();
            if (length <= 0) {
                return 0;
            }
            long position = masterPositionMillis - compositionFile.getOffsetMillis();
            if (position <= 0) {
                return 0;
            }
            return Math.floorMod(position, length);
        }

        private void seekSlaveTo(long positionMillis) {
            if (slavePipeline != null) {
                slavePipeline.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT), positionMillis * 1_000_000L);
                // The flushing seek started a new segment at rate 1
                applySlavePlaybackRate();
            }
        }

        // Follow the master's trimmed playback rate, so the slave keeps its phase while the master
        // is being pulled back into sync with an external timecode
        void setPlaybackRate(double rate) {
            slavePlaybackRate = rate;
            applySlavePlaybackRate();
        }

        private void applySlavePlaybackRate() {
            if (slavePipeline != null && slavePlaybackRate != 1) {
                applyPlaybackRate(slavePipeline, slavePlaybackRate);
            }
        }

        // The slave reached its own end -> loop straight back to the start (it stays phase-aligned
        // because it has been playing at the shared clock rate).
        void loop() {
            if (stopped || slavePipeline == null) {
                return;
            }
            if (hasH265) {
                // H.265 can't seek on the Pi -> recreate. Must run off the bus-callback thread to
                // avoid deadlocking pipeline disposal.
                recreateExecutor.execute(() -> recreate(0));
            } else {
                seekSlaveTo(0);
            }
        }

        // The master was seeked/looped -> jump to the in-loop position matching its new position.
        void rephaseTo(long masterPositionMillis) {
            if (stopped || slavePipeline == null) {
                return;
            }
            if (hasH265) {
                // H.265 can't seek -> recreate (it can only restart from the beginning).
                recreateExecutor.execute(() -> recreate(masterPositionMillis));
            } else {
                seekSlaveTo(phasePositionMillis(masterPositionMillis));
            }
        }

        private void recreate(long masterPositionMillis) {
            synchronized (rebuildLock) {
                // Stopped meanwhile: nothing plays on
                if (stopped) {
                    return;
                }

                try {
                    stop();
                    build();
                    startSyncedToMaster(masterPositionMillis);
                } catch (Exception e) {
                    logger.error("Could not recreate slave pipeline for " + compositionFile.getName(), e);
                }
            }
        }

        void pause() {
            if (slavePipeline != null) {
                slavePipeline.pause();
            }
        }

        // Resume from pause without re-phasing: the shared clock preserved alignment while paused.
        void resume() {
            if (slavePipeline != null) {
                slavePipeline.setState(State.PLAYING);
            }
        }

        // Mute the outputs (avoids audio artifacts before a coordinated teardown)
        void mute() {
            for (Element volume : volumeList) {
                volume.set("volume", 0.0);
            }
        }

        void stop() {
            if (slavePipeline != null) {
                slavePipeline.stop();
                slavePipeline.dispose();
                slavePipeline = null;
            }
            volumeList.clear();
        }

        boolean hasH265() {
            return hasH265;
        }
    }

}
