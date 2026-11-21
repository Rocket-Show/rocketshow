package com.ascargon.rocketshow.lighting.engine;

import com.ascargon.rocketshow.audio.AudioAnalysisService;
import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.designer.BeatClock;
import com.ascargon.rocketshow.lighting.designer.CompositionHold;
import com.ascargon.rocketshow.lighting.designer.DesignerProjectJson;
import com.ascargon.rocketshow.lighting.designer.DmxOverride;
import com.ascargon.rocketshow.lighting.designer.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The shared lighting engine playing the project of the designer service (see docs/lighting-engine.md): it works out
 * the universes Rocket Show sends.
 * <p>
 * The designer service hands over the project when it loads one, the events with their moments (the scenes picked by
 * hand, the tempo, the taps of the beat clock) and, for each frame, what plays in it (a Frame). Everything runs on a
 * thread of its own, one task after the other, so the send timer never waits for the engine: it asks for the frame
 * of the moment it sends next, while it sends the one worked out before (see take). A frame is dropped while the one
 * before it is still being worked out; the timer then sends what it sent last again.
 * <p>
 * A fault of the engine (a trap) is caught: it is logged, and the engine is loaded anew with the project playing, once.
 * If it fails again, the engine pauses until the next project is loaded, while audio and video play on.
 */
public class EngineRenderer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EngineRenderer.class);

    // the frames of audio handed over again each frame, for the ones measured after the frame which needed them
    private static final int AUDIO_FRAMES_AGAIN = 5;
    private static final int AUDIO_FRAMES_BACK = 300;

    // beats are handed over modulo a number of beats which every length of an effect and every bar in beats divides,
    // small enough for single precision (see alignBeats)
    private static final double BEATS_PERIOD = 5040;

    // the frames rendered to warm the engine up (see warmUp)
    private static final int WARM_UP_FRAMES = 300;
    private static final int WARM_UP_PROJECTS = 3;
    private static final AtomicBoolean warmedUp = new AtomicBoolean();

    // what plays in a frame
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
     * What plays in a frame and the moment it is worked out for. The lists are copies (or lists never changed).
     *
     * @param generation         which load of the designer service asked for it (see take)
     * @param nowMillis          the real time the rest was taken at
     * @param renderMillis       the real time the frame is worked out for: when it will be sent
     * @param positionMillis     the position of the timeline (or the clock of the preview) at nowMillis
     * @param running            whether the position runs on from there (not holding or paused)
     * @param compositionName    the composition of the timeline (TIMELINE)
     * @param player             whether a composition player plays it (every track), not the preview of the designer
     * @param defaultComposition whether it is the composition played in between
     * @param holds              the holds the timeline went through, with the time held there until nowMillis
     * @param holding            whether the timeline holds at the last of them
     * @param masterDimmer       the master dimmer of the Play screen (1 for anything but the scenes picked by hand)
     * @param outsideBeatsPerMinute the tempo from outside (null: none)
     */
    public record Frame(long generation, double nowMillis, double renderMillis, double positionMillis, boolean running,
                        Mode mode, String compositionName, boolean player, boolean defaultComposition,
                        String presetUuid, String stepUuid, List<String> sceneUuids, boolean stepPreviewRunning,
                        double stepPreviewStartMillis, List<CompositionHold> holds, boolean holding,
                        List<String> identifyFixtureUuids, List<String> resetFixtureUuids,
                        List<String> lampOnFixtureUuids, List<DmxOverride> dmxOverrides, double masterDimmer,
                        Double outsideBeatsPerMinute) {
    }

    /**
     * A frame worked out: the universes of the project for its moment, and whether the scenes picked by hand have all
     * faded out by then.
     */
    public static final class Rendered {

        private final long generation;
        private final double renderMillis;
        private final boolean liveIdle;
        private final byte[] universes;
        private final Map<String, Integer> universeOffsets;

        Rendered(long generation, double renderMillis, boolean liveIdle, byte[] universes, Map<String, Integer> universeOffsets) {
            this.generation = generation;
            this.renderMillis = renderMillis;
            this.liveIdle = liveIdle;
            this.universes = universes;
            this.universeOffsets = universeOffsets;
        }

        public double getRenderMillis() {
            return renderMillis;
        }

        public boolean isLiveIdle() {
            return liveIdle;
        }

        // the value of a channel (from 0) of a universe of the project, or -1 for a universe the show doesn't send
        public int getValue(String universeUuid, int channel) {
            Integer offset = universeOffsets.get(universeUuid);

            if (offset == null || channel < 0 || channel >= EngineShow.UNIVERSE_SIZE) {
                return -1;
            }

            return universes[offset + channel] & 0xff;
        }

        /**
         * Write the frame to the passed universes: each universe by the uuid it is mapped to, the first one mapped to
         * it, all of its 512 channels. A universe the project puts nothing on is left empty.
         */
        public void writeTo(List<LightingUniverseState> lightingUniverses) {
            Set<String> written = new HashSet<>();

            for (LightingUniverseState lightingUniverse : lightingUniverses) {
                lightingUniverse.reset();
                Integer offset = universeOffsets.get(lightingUniverse.getMappingUuid());

                if (offset == null || !written.add(lightingUniverse.getMappingUuid())) {
                    continue;
                }

                for (int channel = 0; channel < EngineShow.UNIVERSE_SIZE; channel++) {
                    lightingUniverse.getUniverse().put(channel, universes[offset + channel] & 0xff);
                }
            }
        }

    }

    /**
     * What a project needs besides itself when it is loaded.
     *
     * @param audioBuses            the audio buses handed to the engine as its one stream of audio, added together
     *                              (none: the project follows no audio)
     * @param outsideBeatsPerMinute the tempo from outside at the load (null: none)
     */
    public record Load(Collection<String> audioBuses, Double outsideBeatsPerMinute) {
    }

    private final Supplier<LightingEngine> loader;
    private final AudioAnalysisService audioAnalysisService;
    private final BeatClock beatClock;
    private final ExecutorService executor;
    private final AtomicBoolean framePending = new AtomicBoolean();
    private final AtomicReference<Rendered> rendered = new AtomicReference<>();

    // ---- on the thread of the engine ----

    private LightingEngine engine;
    private DesignerEngine designerEngine;

    // the project playing: its JSON as handed to the compiler, its name and what came with it
    private String projectJson;
    private String projectName;
    private double projectLoadMillis;
    private Load load;

    // the show compiled last, with the JSON it was compiled from: a project loaded again is not compiled again
    private String compiledJson;
    private CompiledShow compiled;

    // the engine faulted with the project playing and was loaded anew; failing again, it pauses until the next project
    private boolean restarted;
    private boolean failed;

    // what was handed to the engine last
    private Frame applied;
    private Mode appliedMode;
    private Double appliedTempo;
    private int fedAudioFrame = -1;
    private double liveSpeed = 1;
    private Map<String, Double> liveDimmers = Map.of();

    // the scenes picked by hand, for an engine loaded anew after a fault
    private boolean live;
    private List<String> liveScenes = List.of();
    private List<String> flashedScenes = List.of();

    // how many frames were worked out, and how long the last one took (for the log)
    private long frames;
    private long renderNanos;
    private long slowestRenderNanos;
    private final AtomicLong droppedFrames = new AtomicLong();
    private long lastLogMillis;

    public EngineRenderer(Supplier<LightingEngine> loader, AudioAnalysisService audioAnalysisService, BeatClock beatClock) {
        this.loader = loader;
        this.audioAnalysisService = audioAnalysisService;
        this.beatClock = beatClock;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "lighting-engine");
            thread.setDaemon(true);
            // the lights go before what else the JVM does, like the timer sending them
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        });
    }

    /**
     * An engine renderer with engine.wasm of this build.
     */
    public static EngineRenderer createPacked(AudioAnalysisService audioAnalysisService, BeatClock beatClock) {
        return new EngineRenderer(LightingEngine::loadPacked, audioAnalysisService, beatClock);
    }

    // run a task on the thread of the engine: nothing of it reaches the caller
    private void run(String what, Runnable task) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    fault(what, e);
                }
            });
        } catch (Throwable e) {
            // shut down
            logger.debug("Could not hand '{}' to the lighting engine", what, e);
        }
    }

    // The engine failed: its module may be broken, so it is loaded anew with the project playing at the next frame,
    // once. Failing again, the engine pauses until the next project is loaded.
    private void fault(String what, Throwable e) {
        closeDesignerEngine();
        engine = null;
        compiled = null;
        compiledJson = null;

        if (restarted || projectJson == null) {
            failed = true;
            logger.error("The lighting engine failed at '{}' with the project '{}' and pauses until the next project is loaded", what, projectName, e);
        } else {
            restarted = true;
            logger.error("The lighting engine failed at '{}' with the project '{}' and is loaded anew", what, projectName, e);
        }
    }

    private void closeDesignerEngine() {
        if (designerEngine != null) {
            try {
                designerEngine.close();
            } catch (Throwable e) {
                logger.debug("Could not close the lighting engine", e);
            }

            designerEngine = null;
        }
    }

    // ---- what the designer service hands over ----

    /**
     * A project was loaded (migrated to the current version). Its JSON is taken now, the rest happens on the thread of
     * the engine: the project is compiled (unless it was compiled last) and played from the next frame on.
     */
    public void load(Project project, double nowMillis, Load load) {
        String json;

        try {
            json = DesignerProjectJson.writeForEngine(project);
        } catch (Throwable e) {
            logger.error("Could not hand the project '{}' to the lighting engine", project.getName(), e);
            return;
        }

        String name = project.getName();

        run("load", () -> {
            boolean same = designerEngine != null && json.equals(projectJson);

            projectJson = json;
            projectName = name;
            projectLoadMillis = nowMillis;
            this.load = load;
            restarted = false;
            failed = false;
            live = false;
            liveScenes = List.of();
            flashedScenes = List.of();
            rendered.set(null);

            if (same) {
                // the same show plays on: what plays is handed over anew with the next frame
                forgetApplied();
                designerEngine.clearAudio();
                alignBeats(System.currentTimeMillis());
                return;
            }

            closeDesignerEngine();
            start(nowMillis);
        });
    }

    // the engine for the project playing: the module loaded (once), the project compiled (unless it was compiled last)
    private void start(double nowMillis) {
        forgetApplied();

        if (engine == null) {
            long start = System.nanoTime();
            engine = loader.get();
            logger.info("Loaded the shared lighting engine (revision {}) in {} ms", LightingEngine.packedRevision(), (System.nanoTime() - start) / 1_000_000);
        }

        long start = System.nanoTime();
        boolean compile = compiled == null || !projectJson.equals(compiledJson);

        if (compile) {
            compiled = engine.compile(projectJson);
            compiledJson = projectJson;

            if (compiled.isShow() && !compiled.message().isEmpty()) {
                logger.info("The lighting engine compiled the project '{}' with warnings: {}", projectName, compiled.message());
            }
        }

        if (!compiled.isShow()) {
            // a project the engine can't play: nothing of it plays until the next project is loaded
            failed = true;
            logger.error("The lighting engine can't play the project '{}' ({}): {}", projectName, compiled.code(), compiled.message());
            return;
        }

        designerEngine = new DesignerEngine(engine, compiled, Math.min(projectLoadMillis, nowMillis));
        alignBeats(nowMillis);

        if (live) {
            // (after a fault) the scenes picked by hand fade in again
            designerEngine.playLive(nowMillis);
            appliedMode = Mode.LIVE;
            designerEngine.setLiveScenes(nowMillis, liveScenes, flashedScenes);
        }

        logger.info("The lighting engine plays the project '{}' ({} in {} ms)", projectName, compile ? "compiled" : "compiled before", (System.nanoTime() - start) / 1_000_000);
    }

    private void forgetApplied() {
        applied = null;
        appliedMode = null;
        appliedTempo = load == null ? null : load.outsideBeatsPerMinute();
        fedAudioFrame = -1;
    }

    // The beat clock of the engine starts where the beat clock of Rocket Show is (see BeatClock): at its tempo (handed
    // over as one from outside, then the one from outside as it is, which leaves the clock at it) and its beats
    private void alignBeats(double nowMillis) {
        designerEngine.setTempo(nowMillis, beatClock.getBeatsPerMinute());
        designerEngine.setTempo(nowMillis, appliedTempo);
        double beats = beatClock.getBeats(nowMillis);
        designerEngine.setBeats(nowMillis, beats - Math.floor(beats / BEATS_PERIOD) * BEATS_PERIOD);

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
            closeDesignerEngine();
            projectJson = null;
            projectName = null;
            live = false;
            rendered.set(null);
        });
    }

    // the scenes picked by hand start anew at a moment
    public void startLive(double nowMillis) {
        run("startLive", () -> {
            live = true;
            liveScenes = List.of();
            flashedScenes = List.of();

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
            liveScenes = scenes;
            flashedScenes = flashed;

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

    // ---- a frame ----

    /**
     * Work out a frame on the thread of the engine (see take). Dropped while the one before is still being worked out.
     */
    public void frame(Frame frame) {
        if (!framePending.compareAndSet(false, true)) {
            droppedFrames.incrementAndGet();
            return;
        }

        try {
            executor.execute(() -> {
                try {
                    render(frame);
                } catch (Throwable e) {
                    fault("frame", e);
                } finally {
                    framePending.set(false);
                }
            });
        } catch (Throwable e) {
            // shut down
            framePending.set(false);
        }
    }

    /**
     * The frame worked out last for the passed generation, once: null while there is none (yet), or if the one there
     * was asked for by a load before.
     */
    public Rendered take(long generation) {
        Rendered frame = rendered.getAndSet(null);
        return frame == null || frame.generation != generation ? null : frame;
    }

    private void render(Frame frame) {
        if (failed || projectJson == null) {
            return;
        }

        if (designerEngine == null) {
            // loaded anew after a fault
            start(frame.nowMillis());

            if (designerEngine == null) {
                return;
            }
        }

        long start = System.nanoTime();
        apply(frame);
        designerEngine.render(frame.renderMillis());
        boolean liveIdle = frame.mode() != Mode.LIVE || designerEngine.isLiveIdle(frame.renderMillis());
        rendered.set(new Rendered(frame.generation(), frame.renderMillis(), liveIdle, designerEngine.copyUniverses(), designerEngine.getUniverseOffsets()));

        long took = System.nanoTime() - start;
        frames++;
        renderNanos += took;
        slowestRenderNanos = Math.max(slowestRenderNanos, took);
        logFrameTimes();
    }

    // how long the frames take, once a minute at debug level
    private void logFrameTimes() {
        long now = System.currentTimeMillis();

        if (now - lastLogMillis < 60_000 || !logger.isDebugEnabled()) {
            return;
        }

        if (lastLogMillis > 0 && frames > 0) {
            logger.debug("Lighting engine: {} frames of the project '{}' in the last minute, {} ms each on average, the slowest {} ms, {} dropped",
                    frames, projectName, Math.round(renderNanos / (double) frames / 10_000) / 100.0,
                    Math.round(slowestRenderNanos / 10_000.0) / 100.0, droppedFrames.get());
        }

        lastLogMillis = now;
        frames = 0;
        renderNanos = 0;
        slowestRenderNanos = 0;
        droppedFrames.set(0);
    }

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

        // the position, which the timeline and the preview are at (the scenes picked by hand run on a clock of their
        // own): the engine runs on from there up to the moment of the frame
        if (frame.mode() != Mode.LIVE) {
            engine.setPosition(frame.nowMillis(), frame.positionMillis(), frame.running());
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

    // the frames of audio measured up to the position of the frame, as one stream
    private void feedAudio(Frame frame) {
        Collection<String> buses = load == null ? null : load.audioBuses();

        if (audioAnalysisService == null || buses == null || buses.isEmpty() || frame.mode() != Mode.TIMELINE) {
            return;
        }

        double positionMillis = frame.positionMillis() + (frame.running() ? frame.renderMillis() - frame.nowMillis() : 0);
        int current = (int) Math.floor(Math.max(0, positionMillis) / AudioAnalysisService.FRAME_MILLIS);
        int from = Math.max(Math.max(0, current - AUDIO_FRAMES_BACK), fedAudioFrame - AUDIO_FRAMES_AGAIN);

        if (current < fedAudioFrame - AUDIO_FRAMES_BACK) {
            // the position jumped back
            designerEngine.clearAudio();
            from = Math.max(0, current - AUDIO_FRAMES_BACK);
        }

        for (int index = from; index <= current; index++) {
            double[] values = audioAnalysisService.frame(buses, index);

            if (values != null) {
                designerEngine.audioFrame(index, values);
            }
        }

        fedAudioFrame = current;
    }

    // ---- warming the engine up ----

    /**
     * Load the module and warm the engine up on a thread of its own, once in the life of the JVM: the first frames run
     * before the JVM has compiled the engine, which keeps several cores busy for a while. Rendering a few hundred frames
     * of the passed projects (their JSON, as handed to the compiler) does that before a show, in an engine of their own.
     */
    public static void warmUp(Supplier<LightingEngine> loader, Supplier<List<String>> projectJsons) {
        if (!warmedUp.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                long start = System.nanoTime();
                LightingEngine engine = loader.get();
                int frames = 0;

                for (String json : projectJsons.get()) {
                    if (frames >= WARM_UP_FRAMES * WARM_UP_PROJECTS) {
                        break;
                    }

                    CompiledShow compiled = engine.compile(json);

                    if (!compiled.isShow()) {
                        continue;
                    }

                    try (DesignerEngine warmUp = new DesignerEngine(engine, compiled, 0)) {
                        warmUp.previewScenes(null, null, warmUp.getIndex().scenes());

                        for (int frame = 0; frame < WARM_UP_FRAMES; frame++, frames++) {
                            warmUp.setPosition(frame * 25, frame * 25, false);
                            warmUp.render(frame * 25);
                        }
                    }
                }

                logger.info("Warmed the lighting engine up with {} frames in {} ms", frames, (System.nanoTime() - start) / 1_000_000);
            } catch (Throwable e) {
                logger.warn("Could not warm the lighting engine up", e);
            }
        }, "lighting-engine-warm-up");

        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    // ---- for the tests ----

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

            while (!done.get() && System.currentTimeMillis() < until) {
                lock.wait(100);
            }
        }
    }

    @Override
    public void close() {
        run("close", this::closeDesignerEngine);
        executor.shutdown();
    }

    // the list of the passed holds with their times held, copied
    public static List<CompositionHold> copyOf(List<CompositionHold> holds) {
        List<CompositionHold> copy = new ArrayList<>();

        for (CompositionHold hold : holds) {
            copy.add(new CompositionHold(hold.getPositionMillis(), hold.getHeldMillis()));
        }

        return copy;
    }

}
