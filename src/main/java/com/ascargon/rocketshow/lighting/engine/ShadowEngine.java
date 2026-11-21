package com.ascargon.rocketshow.lighting.engine;

import com.ascargon.rocketshow.audio.AudioAnalysisService;
import com.ascargon.rocketshow.lighting.designer.CompositionHold;
import com.ascargon.rocketshow.lighting.designer.DesignerProjectJson;
import com.ascargon.rocketshow.lighting.designer.DmxOverride;
import com.ascargon.rocketshow.lighting.designer.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Shadow mode (step 3 of docs/lighting-engine.md): the shared lighting engine plays the project of the designer service
 * beside the Java engine, which still drives the lights, and every frame in which the two differ by more than 1 on a
 * channel is logged, with the composition, the position, the universe, the channel, the fixture on it and both values.
 * <p>
 * The designer service hands over what it plays: the project when it loads one, the events with their moments (the
 * scenes picked by hand, the tempo, the taps) and, after each frame it sent, what played in it and the universes it
 * came to (a Frame). Everything runs on a thread of its own, one task after the other, so the send timer never waits
 * for the new engine: a frame is dropped while the one before it is still being worked out. Whatever the new engine
 * does, a fault included, is caught here: it is logged once and shadow mode pauses until the next project is loaded.
 * <p>
 * Turned on by the setting designerShadowEngine (off by default), and only where this build packs engine.wasm.
 */
public class ShadowEngine implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ShadowEngine.class);

    // a value may differ by 1 (single against double precision, see section 5 of the document)
    static final int TOLERANCE = 1;

    // the differences named in a line of the log, and how often a line is logged at most
    private static final int LOGGED_CHANNELS = 8;
    private static final long LOG_INTERVAL_MILLIS = 1000;

    // the frames of audio handed over again each frame, for the ones measured after the frame which needed them
    private static final int AUDIO_FRAMES_AGAIN = 5;
    private static final int AUDIO_FRAMES_BACK = 300;

    // beats are handed over modulo a number of beats which every length of an effect and every bar in beats divides,
    // small enough for single precision (see alignBeats)
    private static final double BEATS_PERIOD = 5040;

    // what plays in a frame (see DefaultDesignerService.getPresets)
    public enum Mode {
        // the timeline of a composition
        TIMELINE,
        // the scenes picked by hand
        LIVE,
        // the preset selected in the designer alone
        PREVIEW_PRESET,
        // the scenes selected in the designer (or the preset selected, while none is)
        PREVIEW_SCENES
    }

    /**
     * What the Java engine played in a frame and what it came to. The lists are copies (or lists never changed).
     *
     * @param nowMillis          the real time of the frame
     * @param timeMillis         the position of the timeline (or the clock of the preview)
     * @param compositionName    the composition of the timeline (TIMELINE)
     * @param player             whether a composition player plays it (every track), not the preview of the designer
     * @param defaultComposition whether it is the composition played in between
     * @param holds              the holds the timeline went through, with the time held there
     * @param holding            whether the timeline holds at the last of them
     * @param masterDimmer       the master dimmer of the Play screen (1 for anything but the scenes picked by hand)
     * @param outsideBeatsPerMinute the tempo from outside (null: none)
     * @param universes          the channels the Java engine sent, by the uuid of the universe of the project (0 for a
     *                           channel it put nothing on)
     */
    public record Frame(double nowMillis, double timeMillis, Mode mode, String compositionName, boolean player,
                        boolean defaultComposition, String presetUuid, String stepUuid, List<String> sceneUuids,
                        boolean stepPreviewRunning, double stepPreviewStartMillis, List<CompositionHold> holds,
                        boolean holding, List<String> identifyFixtureUuids, List<String> resetFixtureUuids,
                        List<String> lampOnFixtureUuids, List<DmxOverride> dmxOverrides, double masterDimmer,
                        Double outsideBeatsPerMinute, Map<String, int[]> universes) {
    }

    /**
     * What a project needs besides itself to be compared.
     *
     * @param channelNames for each universe of the project (by uuid), what is on each of its 512 channels (e.g. the
     *                     name of the fixture and of its channel), for the log
     * @param fineChannels for each universe, the channels sent in several bytes: the channels (from 0) of each, from
     *                     the coarse one to the finest one. They are compared as one number.
     * @param audioBuses   the audio buses handed to the engine as its one stream of audio (none: all measured)
     * @param beats        where the beat clock of the Java engine is at the moment of the load
     * @param beatsPerMinute the tempo it runs at then
     * @param outsideBeatsPerMinute the tempo from outside then (null: none)
     */
    public record Load(Map<String, String[]> channelNames, Map<String, List<int[]>> fineChannels, List<String> audioBuses, double beats, double beatsPerMinute,
                       Double outsideBeatsPerMinute) {
    }

    private final Supplier<LightingEngine> loader;
    private final AudioAnalysisService audioAnalysisService;
    private final ExecutorService executor;
    private final AtomicBoolean framePending = new AtomicBoolean();

    // ---- on the thread of the shadow engine ----

    private LightingEngine engine;
    private DesignerEngine designerEngine;
    private Load load;
    private String projectName;

    // what was handed to the engine last
    private Frame applied;
    private Mode appliedMode;
    private Double appliedTempo;
    private int fedAudioFrame = -1;
    private double liveSpeed = 1;
    private Map<String, Double> liveDimmers = Map.of();

    // what was compared, for the log and the tests
    private volatile long framesCompared;
    private volatile long framesDiffering;
    private volatile int largestDifference;
    private volatile boolean failed;
    private long lastLogMillis;
    private long differingSinceLog;

    public ShadowEngine(Supplier<LightingEngine> loader, AudioAnalysisService audioAnalysisService) {
        this.loader = loader;
        this.audioAnalysisService = audioAnalysisService;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "shadow-lighting-engine");
            thread.setDaemon(true);
            // the show goes first
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /**
     * A shadow engine with engine.wasm of this build, or null if it has none.
     */
    public static ShadowEngine createIfAvailable(AudioAnalysisService audioAnalysisService) {
        if (!LightingEngine.isAvailable()) {
            return null;
        }

        return new ShadowEngine(LightingEngine::loadPacked, audioAnalysisService);
    }

    // run a task on the thread of the shadow engine: nothing of it reaches the caller
    private void run(String what, Runnable task) {
        try {
            executor.execute(() -> {
                if (failed && !what.equals("load")) {
                    return;
                }

                try {
                    task.run();
                } catch (Throwable e) {
                    fail(what, e);
                }
            });
        } catch (Throwable e) {
            // shut down, or no thread to be had: the show plays on without
            logger.debug("Could not hand '{}' to the shadow engine", what, e);
        }
    }

    // the new engine failed: logged once, and shadow mode pauses until the next project is loaded
    private void fail(String what, Throwable e) {
        logger.error("Shadow engine: the shared lighting engine failed at '{}' with the project '{}' and pauses until the next project is loaded", what, projectName, e);
        failed = true;
        closeDesignerEngine();

        // a module which trapped may be broken: it is loaded anew
        engine = null;
    }

    private void closeDesignerEngine() {
        if (designerEngine != null) {
            try {
                designerEngine.close();
            } catch (Throwable e) {
                logger.debug("Could not close the engine of the shadow engine", e);
            }

            designerEngine = null;
        }
    }

    // ---- what the designer service hands over ----

    /**
     * A project was loaded (migrated to the current version). Its JSON is taken now, the rest happens on the thread of
     * the shadow engine.
     */
    public void load(Project project, double nowMillis, Load load) {
        String json;

        try {
            json = DesignerProjectJson.write(project);
        } catch (Throwable e) {
            logger.error("Shadow engine: could not write the project '{}'", project.getName(), e);
            return;
        }

        String name = project.getName();

        run("load", () -> {
            summarize();
            closeDesignerEngine();
            failed = false;
            projectName = name;
            this.load = load;
            applied = null;
            appliedMode = null;
            appliedTempo = load.outsideBeatsPerMinute();
            fedAudioFrame = -1;

            if (engine == null) {
                long start = System.nanoTime();
                engine = loader.get();
                logger.info("Shadow engine: loaded the shared lighting engine (revision {}) in {} ms", LightingEngine.packedRevision(), (System.nanoTime() - start) / 1_000_000);
            }

            long start = System.nanoTime();
            warmUp(json, nowMillis);
            designerEngine = new DesignerEngine(engine, json, nowMillis);
            alignBeats(nowMillis);
            logger.info("Shadow engine: plays the project '{}' beside the Java engine (compiled and warmed up in {} ms)", name, (System.nanoTime() - start) / 1_000_000);
        });
    }

    // the first frames run before the JVM has optimised the engine: a few hundred are rendered on load, in an engine of
    // their own
    private void warmUp(String json, double nowMillis) {
        try (DesignerEngine warmUp = new DesignerEngine(engine, json, nowMillis)) {
            warmUp.previewScenes(null, null, warmUp.getIndex().scenes());

            for (int frame = 0; frame < 300; frame++) {
                warmUp.setPosition(nowMillis + frame * 25, frame * 25, false);
                warmUp.render(nowMillis + frame * 25);
            }
        }
    }

    // The beat clock of the new engine starts where the one of the Java engine is: at its tempo (handed over as one
    // from outside, then the one from outside as it is, which leaves the clock at it) and its beats
    private void alignBeats(double nowMillis) {
        designerEngine.setTempo(nowMillis, load.beatsPerMinute());
        designerEngine.setTempo(nowMillis, load.outsideBeatsPerMinute());
        designerEngine.setBeats(nowMillis, load.beats() - Math.floor(load.beats() / BEATS_PERIOD) * BEATS_PERIOD);

        if (liveSpeed != 1) {
            designerEngine.setLiveSpeed(nowMillis, liveSpeed);
        }

        designerEngine.setLiveDimmers(liveDimmers);
    }

    /**
     * Nothing of a project plays anymore.
     */
    public void unload() {
        run("unload", () -> {
            summarize();
            closeDesignerEngine();
            projectName = null;
        });
    }

    // the scenes picked by hand start anew at a moment (see DefaultDesignerService.startManualScenes)
    public void startLive(double nowMillis) {
        run("startLive", () -> {
            if (designerEngine != null) {
                designerEngine.playLive(nowMillis);
                appliedMode = Mode.LIVE;

                if (liveSpeed != 1) {
                    designerEngine.setLiveSpeed(nowMillis, liveSpeed);
                }
            }
        });
    }

    // the scenes picked by hand and flashed at a moment
    public void setLiveScenes(double nowMillis, List<String> sceneUuids, List<String> flashedSceneUuids) {
        List<String> scenes = List.copyOf(sceneUuids);
        List<String> flashed = List.copyOf(flashedSceneUuids);

        run("setLiveScenes", () -> {
            if (designerEngine != null) {
                designerEngine.setLiveScenes(nowMillis, scenes, flashed);
            }
        });
    }

    public void setLiveDimmers(Map<String, Double> dimmers) {
        Map<String, Double> copy = dimmers == null ? Map.of() : Map.copyOf(dimmers);

        run("setLiveDimmers", () -> {
            liveDimmers = copy;

            if (designerEngine != null) {
                designerEngine.setLiveDimmers(copy);
            }
        });
    }

    public void setLiveSpeed(double nowMillis, double speed) {
        run("setLiveSpeed", () -> {
            liveSpeed = speed;

            if (designerEngine != null) {
                designerEngine.setLiveSpeed(nowMillis, speed);
            }
        });
    }

    // the tempo from outside changed at a moment (null: none)
    public void setTempo(double nowMillis, Double beatsPerMinute) {
        run("setTempo", () -> {
            if (designerEngine != null) {
                designerEngine.setTempo(nowMillis, beatsPerMinute);
                appliedTempo = beatsPerMinute;
            }
        });
    }

    // a beat tapped at a moment (see BeatClock.alignBeat)
    public void tap(double nowMillis) {
        run("tap", () -> {
            if (designerEngine != null) {
                designerEngine.tap(nowMillis);
            }
        });
    }

    // the first beat of a bar at a moment (see BeatClock.beatOne)
    public void beatOne(double nowMillis, boolean jump) {
        run("beatOne", () -> {
            if (designerEngine != null) {
                designerEngine.beatOne(nowMillis, jump);
            }
        });
    }

    // the beats set at a moment (see BeatClock.setBeats)
    public void setBeats(double nowMillis, double beats) {
        run("setBeats", () -> {
            if (designerEngine != null) {
                designerEngine.setBeats(nowMillis, beats - Math.floor(beats / BEATS_PERIOD) * BEATS_PERIOD);
            }
        });
    }

    /**
     * A frame the Java engine sent: the new engine works out the same moment and the two are compared. Dropped while
     * the one before is still being worked out.
     */
    public void frame(Frame frame) {
        if (!framePending.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    if (!failed) {
                        compare(frame);
                    }
                } catch (Throwable e) {
                    fail("frame", e);
                } finally {
                    framePending.set(false);
                }
            });
        } catch (Throwable e) {
            // shut down: the show plays on without
            framePending.set(false);
        }
    }

    // ---- a frame ----

    private void apply(Frame frame) {
        DesignerEngine engine = designerEngine;
        Frame before = applied;

        // what plays
        boolean modeChanged = before == null || frame.mode() != appliedMode;

        switch (frame.mode()) {
            case TIMELINE -> {
                if (modeChanged || !Objects.equals(before.compositionName(), frame.compositionName()) || before.player() != frame.player()
                        || before.defaultComposition() != frame.defaultComposition()) {
                    engine.playTimeline(frame.compositionName(), frame.player(), frame.defaultComposition());
                }
            }
            case LIVE -> {
                if (appliedMode != Mode.LIVE) {
                    // (the scenes picked by hand start with startLive: this is a frame which came first)
                    engine.playLive(frame.nowMillis());
                }
            }
            case PREVIEW_PRESET, PREVIEW_SCENES -> {
                if (modeChanged || !Objects.equals(before.presetUuid(), frame.presetUuid()) || !Objects.equals(before.stepUuid(), frame.stepUuid())
                        || !Objects.equals(before.sceneUuids(), frame.sceneUuids())) {
                    if (frame.mode() == Mode.PREVIEW_PRESET) {
                        engine.previewPreset(frame.presetUuid(), frame.stepUuid());
                    } else {
                        engine.previewScenes(frame.presetUuid(), frame.stepUuid(), frame.sceneUuids());
                    }
                }

                if (modeChanged || before.stepPreviewRunning() != frame.stepPreviewRunning() || before.stepPreviewStartMillis() != frame.stepPreviewStartMillis()) {
                    engine.setStepPreview(frame.stepPreviewRunning(), frame.stepPreviewStartMillis());
                }
            }
        }

        appliedMode = frame.mode();

        // the position, which the timeline and the preview are at (the scenes picked by hand run on a clock of their own)
        if (frame.mode() != Mode.LIVE) {
            engine.setPosition(frame.nowMillis(), frame.timeMillis(), false);
        }

        // the holds: handed over anew while there are any, the time held growing
        if (!frame.holds().isEmpty() || (before != null && !before.holds().isEmpty())) {
            engine.setHolds(frame.nowMillis(), frame.holds(), frame.holding());
        }

        if (before == null || !Objects.equals(before.identifyFixtureUuids(), frame.identifyFixtureUuids())) {
            engine.setIdentifyFixtures(frame.identifyFixtureUuids());
        }

        if (before == null || !Objects.equals(before.resetFixtureUuids(), frame.resetFixtureUuids()) || !Objects.equals(before.lampOnFixtureUuids(), frame.lampOnFixtureUuids())) {
            engine.setMaintenanceFixtures(frame.resetFixtureUuids(), frame.lampOnFixtureUuids());
        }

        if (before == null || before.dmxOverrides() != frame.dmxOverrides()) {
            engine.setDmxOverrides(frame.dmxOverrides());
        }

        if (before == null || before.masterDimmer() != frame.masterDimmer()) {
            engine.setMasterDimmer(frame.masterDimmer());
        }

        if (!Objects.equals(appliedTempo, frame.outsideBeatsPerMinute())) {
            // (a tempo changed without its event, which would have come first)
            engine.setTempo(frame.nowMillis(), frame.outsideBeatsPerMinute());
            appliedTempo = frame.outsideBeatsPerMinute();
        }

        feedAudio(frame);
        applied = frame;
    }

    // the frames of audio measured up to the moment of the frame, as one stream
    private void feedAudio(Frame frame) {
        if (audioAnalysisService == null || !audioAnalysisService.isRequired()) {
            return;
        }

        int current = (int) Math.floor(Math.max(0, frame.timeMillis()) / AudioAnalysisService.FRAME_MILLIS);
        int from = Math.max(Math.max(0, current - AUDIO_FRAMES_BACK), fedAudioFrame - AUDIO_FRAMES_AGAIN);

        if (current < fedAudioFrame - AUDIO_FRAMES_BACK) {
            // the position jumped back
            designerEngine.clearAudio();
            from = Math.max(0, current - AUDIO_FRAMES_BACK);
        }

        List<String> buses = load == null || load.audioBuses() == null ? List.of() : load.audioBuses();

        for (int index = from; index <= current; index++) {
            double[] values = audioAnalysisService.frame(buses, index);

            if (values != null) {
                designerEngine.audioFrame(index, values);
            }
        }

        fedAudioFrame = current;
    }

    // a channel (the coarse one of a channel with fine ones, which are compared as one number with it) and its values
    private record Difference(String universeUuid, int channel, int bytes, long java, long engine) {
    }

    // the value of a channel sent in several bytes, from the coarse one to the finest one
    private static long value(int[] addresses, java.util.function.IntUnaryOperator bytes) {
        long value = 0;

        for (int address : addresses) {
            value = value * 256 + bytes.applyAsInt(address);
        }

        return value;
    }

    private void compare(Frame frame) {
        if (designerEngine == null) {
            return;
        }

        apply(frame);
        designerEngine.render(frame.nowMillis());

        List<Difference> differences = new ArrayList<>();
        int largest = 0;

        for (String universeUuid : designerEngine.getUniverseUuids()) {
            int[] java = frame.universes().get(universeUuid);

            if (java == null) {
                // a universe of the project no universe of the settings is mapped to: nothing sends it
                continue;
            }

            // a channel with fine channels as one number, within 1 of its resolution
            boolean[] compared = new boolean[EngineShow.UNIVERSE_SIZE];
            List<int[]> fineChannels = load == null || load.fineChannels() == null ? null : load.fineChannels().get(universeUuid);

            for (int[] addresses : fineChannels == null ? List.<int[]>of() : fineChannels) {
                long javaValue = value(addresses, address -> java[address]);
                long engineValue = value(addresses, address -> designerEngine.getValue(universeUuid, address));
                long difference = Math.abs(javaValue - engineValue);

                if (difference > TOLERANCE) {
                    differences.add(new Difference(universeUuid, addresses[0], addresses.length, javaValue, engineValue));
                    largest = (int) Math.max(largest, Math.min(Integer.MAX_VALUE, difference));
                }

                for (int address : addresses) {
                    compared[address] = true;
                }
            }

            for (int channel = 0; channel < EngineShow.UNIVERSE_SIZE; channel++) {
                if (compared[channel]) {
                    continue;
                }

                int value = designerEngine.getValue(universeUuid, channel);
                int difference = Math.abs(value - java[channel]);

                if (difference > TOLERANCE) {
                    differences.add(new Difference(universeUuid, channel, 1, java[channel], value));
                    largest = Math.max(largest, difference);
                }
            }
        }

        framesCompared++;

        if (differences.isEmpty()) {
            return;
        }

        framesDiffering++;
        differingSinceLog++;
        largestDifference = Math.max(largestDifference, largest);

        long now = System.currentTimeMillis();

        if (now - lastLogMillis < LOG_INTERVAL_MILLIS) {
            return;
        }

        lastLogMillis = now;
        logger.warn("Shadow engine: {} channels differ by more than {} (up to {}) in the project '{}', {} at {} ms{} (real time {}): {}",
                differences.size(), TOLERANCE, largest, projectName, describe(frame), Math.round(frame.timeMillis() * 1000) / 1000.0,
                differingSinceLog > 1 ? ", " + (differingSinceLog - 1) + " other frames differed since the last line" : "",
                Math.round(frame.nowMillis()), describe(differences));
        differingSinceLog = 0;
    }

    private static String describe(Frame frame) {
        return switch (frame.mode()) {
            case TIMELINE -> "the composition '" + frame.compositionName() + "'" + (frame.player() ? "" : " previewed") + (frame.holding() ? " holding" : "");
            case LIVE -> "the scenes picked by hand";
            case PREVIEW_PRESET -> "the preview of the preset " + frame.presetUuid();
            case PREVIEW_SCENES -> "the preview of the scenes " + frame.sceneUuids();
        };
    }

    private String describe(List<Difference> differences) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < differences.size() && i < LOGGED_CHANNELS; i++) {
            Difference difference = differences.get(i);
            String[] names = load == null || load.channelNames() == null ? null : load.channelNames().get(difference.universeUuid());
            String name = names == null || names[difference.channel()] == null ? "" : " " + names[difference.channel()];

            text.append(i == 0 ? "" : "; ")
                    .append("universe ").append(difference.universeUuid())
                    .append(" channel ").append(difference.channel()).append(" (DMX address ").append(difference.channel() + 1).append(name).append(")")
                    .append(": Java ").append(difference.java()).append(", engine ").append(difference.engine())
                    .append(difference.bytes() > 1 ? " (of " + ((1L << (8 * difference.bytes())) - 1) + ")" : "");
        }

        if (differences.size() > LOGGED_CHANNELS) {
            text.append("; and ").append(differences.size() - LOGGED_CHANNELS).append(" more");
        }

        return text.toString();
    }

    // what was compared of the project played so far
    private void summarize() {
        if (projectName != null && framesCompared > 0) {
            logger.info("Shadow engine: {} frames of the project '{}' compared so far, {} of them differed by more than {} (up to {})",
                    framesCompared, projectName, framesDiffering, TOLERANCE, largestDifference);
        }
    }

    // ---- for the tests ----

    public long getFramesCompared() {
        return framesCompared;
    }

    public long getFramesDiffering() {
        return framesDiffering;
    }

    public int getLargestDifference() {
        return largestDifference;
    }

    public boolean isFailed() {
        return failed;
    }

    /**
     * Wait for what was handed over so far to be done with.
     */
    public void awaitIdle() throws InterruptedException {
        AtomicBoolean done = new AtomicBoolean();
        Object lock = new Object();

        run("await", () -> {
            synchronized (lock) {
                done.set(true);
                lock.notifyAll();
            }
        });

        synchronized (lock) {
            long until = System.currentTimeMillis() + 30_000;

            while (!done.get() && System.currentTimeMillis() < until && !failed) {
                lock.wait(100);
            }
        }
    }

    @Override
    public void close() {
        run("close", () -> {
            summarize();
            closeDesignerEngine();
        });
        executor.shutdown();
    }

}
