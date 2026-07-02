package com.ascargon.rocketshow.play;

import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.audio.AudioDevice;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.gstreamer.GstApi;
import com.ascargon.rocketshow.midi.MidiMapping;
import com.ascargon.rocketshow.midi.MidiMessageParser;
import com.ascargon.rocketshow.midi.MidiRouter;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final CompositionPipelineBuilder builder;
    private final Composition composition;

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

    // True when a hardware H.265 decoder is in use in the master pipeline
    private boolean masterHasH265 = false;

    CompositionPipeline(CompositionPipelineBuilder builder, Composition composition, Pipeline masterPipeline,
                        Executor recreateExecutor, MidiMapping midiMapping, MidiMessageParser midiMessageParser) {
        this.builder = builder;
        this.composition = composition;
        this.masterPipeline = masterPipeline;
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

        SlavePipeline(CompositionFile compositionFile, int index) {
            this.compositionFile = compositionFile;
            this.index = index;
        }

        // Construct the single-source pipeline with its own output (sink). Does not start it.
        void build() throws Exception {
            slavePipeline = new Pipeline("slave" + index);

            Bus bus = GstApi.GST_API.gst_element_get_bus(slavePipeline);
            bus.connect((Bus.ERROR) (GstObject source, int code, String message) ->
                    logger.error("GST slave error (" + compositionFile.getName() + "): " + message));
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
            }
        }

        // The slave reached its own end -> loop straight back to the start (it stays phase-aligned
        // because it has been playing at the shared clock rate).
        void loop() {
            if (slavePipeline == null) {
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
            if (slavePipeline == null) {
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
            try {
                stop();
                build();
                startSyncedToMaster(masterPositionMillis);
            } catch (Exception e) {
                logger.error("Could not recreate slave pipeline for " + compositionFile.getName(), e);
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
