package com.ascargon.rocketshow.lighting.engine;

import com.ascargon.rocketshow.lighting.designer.CompositionHold;
import com.ascargon.rocketshow.lighting.designer.DesignerProjectJson;
import com.ascargon.rocketshow.lighting.designer.DmxOverride;
import com.ascargon.rocketshow.lighting.designer.Project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A project of the designer played by the shared lighting engine, with the inputs the designer service drives (see
 * section 4 of docs/lighting-engine.md): the uuids of the project turned into the numbers of the show, real time into
 * the clock of the engine.
 * <p>
 * The project is handed over migrated to the current version (see ProjectMigration) and compiled once; the uuids of
 * the project are turned into the numbers of the show by its index. The moments are milliseconds of real time
 * (System.currentTimeMillis()), the positions milliseconds of the timeline, both passed to the engine as whole
 * microseconds. A frame is worked out by render and read with getValue or copyUniverses.
 * <p>
 * Not thread safe: one thread drives it. Close it when it is done with.
 */
public class DesignerEngine implements AutoCloseable {

    // the moment the clock of the engine counts from: the moments stay small numbers
    private final double epochMillis;

    private final EngineShow show;
    private final ShowIndex index;

    // the universes of the last frame, and where each starts in it, by the uuid of the universe of the project
    private final byte[] universes;
    private final Map<String, Integer> universeOffsets;

    // what was handed to the engine last, to hand over only what changed
    private Set<String> liveScenes = new HashSet<>();
    private Set<String> flashedScenes = new HashSet<>();
    private final Map<String, Set<Integer>> markedFixtures = new HashMap<>();
    private List<int[]> overrides = new ArrayList<>();

    /**
     * Compile the passed project, migrated to the current version, and make an engine playing it. Throws an
     * IllegalArgumentException saying why for a project the engine can't play.
     */
    public DesignerEngine(LightingEngine engine, Project project, double nowMillis) {
        this(engine, DesignerProjectJson.writeForEngine(project), nowMillis);
    }

    /**
     * Compile the passed JSON of a project of the current version and make an engine playing it.
     */
    public DesignerEngine(LightingEngine engine, String projectJson, double nowMillis) {
        this(engine, engine.compile(projectJson), nowMillis);
    }

    /**
     * Make an engine playing a show compiled before (with the same module). Throws an IllegalArgumentException saying
     * why for a project the compiler made no show of.
     */
    public DesignerEngine(LightingEngine engine, CompiledShow compiled, double nowMillis) {
        if (!compiled.isShow()) {
            throw new IllegalArgumentException("The lighting engine can't play the project (" + compiled.code() + "): " + compiled.message());
        }

        this.epochMillis = nowMillis;
        this.index = compiled.index();
        this.show = engine.play(compiled.show(), index, 0);
        this.universes = new byte[show.getUniverseCount() * EngineShow.UNIVERSE_SIZE];
        Map<String, Integer> offsets = new HashMap<>();

        for (int universe = 0; universe < index.universes().size() && universe < show.getUniverseCount(); universe++) {
            offsets.putIfAbsent(index.universes().get(universe), universe * EngineShow.UNIVERSE_SIZE);
        }

        this.universeOffsets = Map.copyOf(offsets);
    }

    public ShowIndex getIndex() {
        return index;
    }

    // a moment of real time on the clock of the engine
    long micros(double realMillis) {
        return Math.max(0, Math.round((realMillis - epochMillis) * 1000));
    }

    // a position of the timeline
    static long positionMicros(double positionMillis) {
        return Math.round(positionMillis * 1000);
    }

    // ---- what plays ----

    /**
     * Play the timeline of the composition with the passed name: as the show (every track) when a composition player
     * plays it, else as the preview of the designer (which leaves the hidden tracks out), and as the composition played
     * in between, which follows a tempo from outside. False for a composition the project hasn't.
     */
    public boolean playTimeline(String compositionName, boolean player, boolean defaultComposition) {
        int composition = index.compositionNamed(compositionName);

        if (composition < 0) {
            show.stop();
            return false;
        }

        show.playTimeline(composition, (player ? EngineShow.TIMELINE_PLAYER : 0) | (defaultComposition ? EngineShow.TIMELINE_DEFAULT_COMPOSITION : 0));
        return true;
    }

    /**
     * Preview the preset selected in the designer alone, at its step being edited (none: the first one), the way the
     * designer previews it.
     */
    public void previewPreset(String presetUuid, String stepUuid) {
        int preset = index.preset(presetUuid);
        show.playPreview(true);
        show.selectPreset(preset, index.step(preset, stepUuid));
    }

    /**
     * Preview the scenes selected in the designer (the preset selected alone while none is).
     */
    public void previewScenes(String presetUuid, String stepUuid, List<String> sceneUuids) {
        int preset = index.preset(presetUuid);
        show.playPreview(false);
        show.selectPreset(preset, index.step(preset, stepUuid));
        show.clearScenes();

        for (String uuid : sceneUuids == null ? List.<String>of() : sceneUuids) {
            int scene = index.scene(uuid);

            if (scene >= 0) {
                show.selectScene(scene, true);
            }
        }
    }

    // whether the steps of the previewed presets run (from a position on) or hold at the step being edited
    public void setStepPreview(boolean running, double startMillis) {
        show.setStepPreview(running, positionMicros(startMillis));
    }

    /**
     * Play the scenes picked by hand, from now on: none is picked yet (see setLiveScenes).
     */
    public void playLive(double nowMillis) {
        show.playLive(micros(nowMillis));
        liveScenes = new HashSet<>();
        flashedScenes = new HashSet<>();
    }

    public void stop() {
        show.stop();
    }

    // ---- the timeline ----

    // where the timeline (or the clock of the preview) is at a moment, and whether it runs on from there
    public void setPosition(double nowMillis, double positionMillis, boolean running) {
        show.setPosition(micros(nowMillis), positionMicros(positionMillis), running);
    }

    // the timeline holds at a hold point until go
    public void hold(double nowMillis, double positionMillis) {
        show.hold(micros(nowMillis), positionMicros(positionMillis));
    }

    public void go(double nowMillis) {
        show.go(micros(nowMillis));
    }

    // the position jumped: the holds from there on weren't gone through anymore
    public void forgetHolds(double fromPositionMillis) {
        show.forgetHolds(positionMicros(fromPositionMillis));
    }

    /**
     * The holds the timeline went through, all of them, and whether it holds at the last of them at the passed moment
     * (for as long already as that hold says).
     */
    public void setHolds(double nowMillis, List<CompositionHold> holds, boolean holding) {
        // (before any position a timeline has)
        show.forgetHolds(-(1L << 50));

        for (CompositionHold hold : holds) {
            show.addHold(positionMicros(hold.getPositionMillis()), Math.round(hold.getHeldMillis() * 1000));
        }

        show.setHolding(micros(nowMillis), holding);
    }

    // ---- the tempo ----

    // the tempo from outside (null: none, the programmed one)
    public void setTempo(double nowMillis, Double beatsPerMinute) {
        show.setTempo(micros(nowMillis), beatsPerMinute == null ? 0 : beatsPerMinute);
    }

    // a beat falls on a moment (a beat tapped)
    public void tap(double nowMillis) {
        show.tap(micros(nowMillis));
    }

    // the first beat of a bar falls on a moment
    public void beatOne(double nowMillis, boolean jump) {
        show.beatOne(micros(nowMillis), jump);
    }

    // where the beats are at a moment, at once
    public void setBeats(double nowMillis, double beats) {
        show.setBeats(micros(nowMillis), beats);
    }

    // ---- the scenes picked by hand ----

    /**
     * The scenes picked by hand and the ones flashed, at a moment: the ones new to the lists fade in (or flash), the
     * ones gone fade out.
     */
    public void setLiveScenes(double nowMillis, List<String> sceneUuids, List<String> flashedSceneUuids) {
        long now = micros(nowMillis);
        liveScenes = updateLive(now, liveScenes, sceneUuids, false);
        flashedScenes = updateLive(now, flashedScenes, flashedSceneUuids, true);
    }

    private Set<String> updateLive(long now, Set<String> before, List<String> uuids, boolean flash) {
        Set<String> after = new HashSet<>(uuids == null ? List.of() : uuids);

        for (String uuid : before) {
            if (!after.contains(uuid)) {
                live(now, uuid, flash, false);
            }
        }

        for (String uuid : after) {
            if (!before.contains(uuid)) {
                live(now, uuid, flash, true);
            }
        }

        return after;
    }

    private void live(long now, String uuid, boolean flash, boolean on) {
        int scene = index.scene(uuid);

        if (scene < 0) {
            return;
        }

        if (flash) {
            show.liveFlash(now, scene, on);
        } else {
            show.liveScene(now, scene, on);
        }
    }

    // the dimmer of each scene picked by hand, by its uuid (1 for the ones not named)
    public void setLiveDimmers(Map<String, Double> dimmers) {
        for (int scene = 0; scene < index.scenes().size(); scene++) {
            Double dimmer = dimmers == null ? null : dimmers.get(index.scenes().get(scene));
            show.liveDimmer(scene, dimmer == null ? 1 : dimmer);
        }
    }

    // the speed master of the scenes picked by hand (1: as written)
    public void setLiveSpeed(double nowMillis, double speed) {
        show.liveSpeed(micros(nowMillis), speed);
    }

    // whether no scene picked by hand plays anymore: none is on, flashed or still fades out
    public boolean isLiveIdle(double nowMillis) {
        return show.isLiveIdle(micros(nowMillis));
    }

    // ---- the rest of the inputs ----

    // the master dimmer of the Play screen (0 to 1), on top of the one of the project
    public void setMasterDimmer(double dimmer) {
        show.setMasterDimmer(dimmer);
    }

    // the fixtures being identified from the fixture pool of the designer
    public void setIdentifyFixtures(List<String> fixtureUuids) {
        mark("identify", fixtureUuids);
    }

    // the fixtures asked to reset themselves or to switch their lamp on
    public void setMaintenanceFixtures(List<String> resetFixtureUuids, List<String> lampOnFixtureUuids) {
        mark("reset", resetFixtureUuids);
        mark("lampOn", lampOnFixtureUuids);
    }

    private void mark(String what, List<String> fixtureUuids) {
        Set<Integer> after = new HashSet<>();

        for (String uuid : fixtureUuids == null ? List.<String>of() : fixtureUuids) {
            after.addAll(index.fixtureNumbers(uuid));
        }

        Set<Integer> before = markedFixtures.getOrDefault(what, Set.of());

        for (int fixture : before) {
            if (!after.contains(fixture)) {
                mark(what, fixture, false);
            }
        }

        for (int fixture : after) {
            if (!before.contains(fixture)) {
                mark(what, fixture, true);
            }
        }

        markedFixtures.put(what, after);
    }

    private void mark(String what, int fixture, boolean on) {
        switch (what) {
            case "identify" -> show.identify(fixture, on);
            case "reset" -> show.resetFixture(fixture, on);
            default -> show.lampOn(fixture, on);
        }
    }

    // the DMX channels held at a value by the faders of the designer
    public void setDmxOverrides(List<DmxOverride> dmxOverrides) {
        for (int[] override : overrides) {
            show.setOverride(override[0], override[1], -1);
        }

        overrides = new ArrayList<>();

        for (DmxOverride dmxOverride : dmxOverrides == null ? List.<DmxOverride>of() : dmxOverrides) {
            int universe = index.universe(dmxOverride.getUniverseUuid());

            if (universe >= 0 && dmxOverride.getDmxChannel() >= 0 && dmxOverride.getDmxChannel() < EngineShow.UNIVERSE_SIZE) {
                int value = Math.max(0, Math.min(255, dmxOverride.getValue()));
                show.setOverride(universe, dmxOverride.getDmxChannel(), value);
                overrides.add(new int[]{universe, dmxOverride.getDmxChannel()});
            }
        }
    }

    // a frame of the analysis of the audio playing (see AudioAnalysisService), for the 20 ms from index * 20 ms of the
    // position played
    public void audioFrame(int index, double[] frame) {
        show.audioFrame(index, frame);
    }

    public void clearAudio() {
        show.clearAudio();
    }

    // ---- a frame ----

    /**
     * Work out a frame for a moment and read its universes.
     */
    public void render(double nowMillis) {
        show.render(micros(nowMillis));
        show.readUniverses(universes);
    }

    /**
     * The universes of the project the show sends, by their uuids.
     */
    public Set<String> getUniverseUuids() {
        return universeOffsets.keySet();
    }

    /**
     * The value of a channel (from 0) of a universe of the project in the last frame, or -1 for a universe the show
     * doesn't send.
     */
    public int getValue(String universeUuid, int channel) {
        Integer offset = universeOffsets.get(universeUuid);

        if (offset == null || channel < 0 || channel >= EngineShow.UNIVERSE_SIZE) {
            return -1;
        }

        return universes[offset + channel] & 0xff;
    }

    /**
     * The universes of the last frame, copied: 512 channels for each universe of the show, one after the other (see
     * getUniverseOffsets).
     */
    public byte[] copyUniverses() {
        return universes.clone();
    }

    /**
     * Where each universe of the project starts in the universes of a frame, by its uuid. Never changed.
     */
    public Map<String, Integer> getUniverseOffsets() {
        return universeOffsets;
    }

    @Override
    public void close() {
        show.close();
    }

}
