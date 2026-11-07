package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The scenes picked by hand on the Play screen while they play.
 * <p>
 * A scene picked fades in over its fade in time and fades out over its fade out time when it is let go, both in real
 * time. Let go while it still fades in, it fades out from where it got to; picked again while it fades out, it fades in
 * from where it is. A scene flashed is on at once while it is held, on top of the others.
 * <p>
 * The presets of a scene play from the moment it is picked, like on a clip of the timeline: their steps start with the
 * first one, their effects at their beginning. What is timed in milliseconds runs on a clock of its own, as fast as the
 * speed master makes it run. What follows the beat (the curves and steps timed in beats) runs on the beats of the
 * tempo playing (see BeatClock), as fast as the speed master makes them run too. A scene picked starts on a whole beat,
 * so the beat-synced curves of all scenes run on the same beats, the ones tapped or of a MIDI clock (at a speed of 1).
 *
 * @author Moritz A. Vieli
 */
public class LiveScenes {

    // where the clips of the scenes end: they play until they are let go, which their fades work out here
    private static final double OPEN_END_MILLIS = 1e15;

    // a scene picked by hand
    private static final class LiveScene {
        private final Scene scene;
        private final boolean flash;

        // where the presets of the scene play from: on the clock of the live scenes, and on its beats (a whole one)
        private final ScenePlaybackRegion region;
        private final double startBeats;

        // the scene without its fades and its dimmer: the fades of its presets are played on it, the ones of the
        // scene are worked out here
        private final Scene plain;

        // when it was picked (moved, so a scene picked again while it fades out fades in from where it is) and let go,
        // in real time (null while it is on)
        private double pickedRealMillis;
        private Double releasedRealMillis;

        private LiveScene(Scene scene, boolean flash, double clockMillis, double beats, double realMillis) {
            this.scene = scene;
            this.flash = flash;
            this.pickedRealMillis = realMillis;

            region = new ScenePlaybackRegion();
            region.setSceneUuid(scene.getUuid());
            region.setStartMillis(clockMillis);
            region.setEndMillis(OPEN_END_MILLIS);
            startBeats = Math.floor(beats + 1e-9);

            plain = new Scene();
            plain.setUuid(scene.getUuid());
            plain.setPresetUuids(scene.getPresetUuids());
        }

        // how far the fade in has come at the passed moment, 1 without one
        private double fadeIn(double realMillis) {
            double fadeInMillis = scene.getFadeInMillis();

            if (flash || fadeInMillis <= 0 || realMillis >= pickedRealMillis + fadeInMillis) {
                return 1;
            }

            return TransitionCurve.apply(scene.getFadeInCurve(), (realMillis - pickedRealMillis) / fadeInMillis);
        }

        // how much of the scene reaches the stage through its fades at the passed moment
        private double level(double realMillis) {
            if (releasedRealMillis == null) {
                return fadeIn(realMillis);
            }

            double fadeOutMillis = scene.getFadeOutMillis();

            if (flash || fadeOutMillis <= 0 || realMillis >= releasedRealMillis + fadeOutMillis) {
                return 0;
            }

            // from where the fade in got to
            double remaining = 1 - (realMillis - releasedRealMillis) / fadeOutMillis;
            return fadeIn(releasedRealMillis) * TransitionCurve.apply(scene.getFadeOutCurve(), remaining);
        }

        private boolean isOver(double realMillis) {
            return releasedRealMillis != null && level(realMillis) <= 0;
        }
    }

    /**
     * A preset of a scene picked by hand, with how much of it reaches the stage at the moment
     */
    public static final class LivePreset extends PresetRegionScene {

        private final PresetRegionScene plain;
        private double sceneLevel;

        // the beats it has played for
        private double beats;

        private LivePreset(Preset preset, LiveScene liveScene) {
            super(preset, liveScene.region, liveScene.scene);
            plain = new PresetRegionScene(preset, liveScene.region, liveScene.plain);
        }

        // how much of the preset reaches the stage: the scene through its fades, its dimmer and the dimmer of the
        // Play screen, and the preset through its own fades (on the clock of the live scenes)
        public double getIntensity(double timeMillis) {
            return sceneLevel * DefaultDesignerService.getPresetIntensity(plain, timeMillis);
        }

        // the time it has played for in beats, as the milliseconds they last at the passed tempo: what follows the beat
        // runs on it, at that tempo
        public double getBeatTimeMillis(double beatsPerMinute) {
            return beats * 60000 / beatsPerMinute;
        }
    }

    private final Map<String, LiveScene> scenes = new LinkedHashMap<>();
    private final Map<String, LiveScene> flashes = new LinkedHashMap<>();

    // the dimmers of the Play screen, by scene (1 for a scene without one)
    private Map<String, Double> dimmers = new HashMap<>();

    // the beats of the tempo playing
    private final BeatClock beatClock;

    // the clock: where it is, the real time it was there at, its beats and the ones of the tempo then, and how fast it
    // runs
    private double clockMillis = 0;
    private Double clockRealMillis;
    private double beats = 0;
    private double clockTempoBeats = 0;
    private double speed = 1;

    public LiveScenes(BeatClock beatClock) {
        this.beatClock = beatClock;
    }

    // ---- the clock ----

    // where the clock is at the passed real time
    public synchronized double advance(double realMillis) {
        double tempoBeats = beatClock.getBeats(realMillis);

        if (clockRealMillis == null) {
            // started on the beats of the tempo
            beats = tempoBeats;
        } else {
            clockMillis += (realMillis - clockRealMillis) * speed;
            beats += (tempoBeats - clockTempoBeats) * speed;
        }

        clockRealMillis = realMillis;
        clockTempoBeats = tempoBeats;
        return clockMillis;
    }

    // the beats of the clock (see advance)
    public synchronized double getBeats() {
        return beats;
    }

    // The speed master. Back at 1, the beats of the scenes fall on the ones of the tempo again (they ran apart while
    // the speed was another one).
    public synchronized void setSpeed(double speed, double realMillis) {
        advance(realMillis);
        this.speed = speed;

        if (speed == 1) {
            beats = clockTempoBeats + Math.round(beats - clockTempoBeats);
        }
    }

    // ---- the scenes ----

    // The scenes on and the ones flashed: the ones which weren't are picked, the ones which aren't anymore let go
    public synchronized void setScenes(List<Scene> on, List<Scene> flashed, double realMillis) {
        double clock = advance(realMillis);
        update(scenes, on, false, clock, realMillis);
        update(flashes, flashed, true, clock, realMillis);
    }

    private void update(Map<String, LiveScene> liveScenes, List<Scene> on, boolean flash, double clock, double realMillis) {
        List<String> onUuids = new ArrayList<>();

        for (Scene scene : on) {
            onUuids.add(scene.getUuid());
            LiveScene liveScene = liveScenes.get(scene.getUuid());

            if (liveScene != null && liveScene.scene != scene) {
                // the scene of another project, or of the project saved anew
                liveScenes.remove(scene.getUuid());
                liveScene = null;
            }

            if (liveScene != null && liveScene.isOver(realMillis)) {
                liveScenes.remove(scene.getUuid());
                liveScene = null;
            }

            if (liveScene == null) {
                liveScenes.put(scene.getUuid(), new LiveScene(scene, flash, clock, beats, realMillis));
            } else if (liveScene.releasedRealMillis != null) {
                // picked again while it fades out: it keeps playing, and fades in from where it is
                double level = liveScene.level(realMillis);
                liveScene.releasedRealMillis = null;
                liveScene.pickedRealMillis = getFadeInStart(scene, level, realMillis);
            }
        }

        for (LiveScene liveScene : liveScenes.values()) {
            if (!onUuids.contains(liveScene.scene.getUuid()) && liveScene.releasedRealMillis == null) {
                liveScene.releasedRealMillis = realMillis;
            }
        }
    }

    // when the fade in of the scene would have started to have come to the passed level by now
    private static double getFadeInStart(Scene scene, double level, double realMillis) {
        double fadeInMillis = scene.getFadeInMillis();

        if (fadeInMillis <= 0 || level >= 1) {
            return realMillis - Math.max(fadeInMillis, 0);
        }

        // the curves only ever rise: find the point on it reaching the level
        double low = 0;
        double high = 1;

        for (int i = 0; i < 30; i++) {
            double middle = (low + high) / 2;

            if (TransitionCurve.apply(scene.getFadeInCurve(), middle) < level) {
                low = middle;
            } else {
                high = middle;
            }
        }

        return realMillis - high * fadeInMillis;
    }

    // the dimmers of the Play screen, by scene
    public synchronized void setDimmers(Map<String, Double> dimmers) {
        this.dimmers = dimmers == null ? new HashMap<>() : new HashMap<>(dimmers);
    }

    // turn all scenes off at once, e.g. for the scenes of another project, and start the clock anew
    public synchronized void reset() {
        scenes.clear();
        flashes.clear();
        clockMillis = 0;
        clockRealMillis = null;
    }

    // whether nothing plays anymore: no scene is on, flashed or still fades out
    public synchronized boolean isIdle(double realMillis) {
        for (LiveScene liveScene : scenes.values()) {
            if (!liveScene.isOver(realMillis)) {
                return false;
            }
        }

        for (LiveScene liveScene : flashes.values()) {
            if (!liveScene.isOver(realMillis)) {
                return false;
            }
        }

        return true;
    }

    /**
     * The presets of the scenes playing, the ones at the bottom first (the ones processed later overwrite them): the
     * scenes in the order of the project, the topmost one last, like the designer layers them, and the scenes flashed
     * on top of them. The scenes which have faded out are forgotten.
     *
     * @param projectScenes all scenes of the project, in their order
     * @param scenePresets  the presets of a scene, in their order (the topmost one first)
     * @param timeMillis    the time on the clock of the live scenes (see advance)
     */
    public synchronized List<PresetRegionScene> getPresets(List<Scene> projectScenes, Function<Scene, List<Preset>> scenePresets, double timeMillis, double realMillis) {
        scenes.values().removeIf(liveScene -> liveScene.isOver(realMillis));
        flashes.values().removeIf(liveScene -> liveScene.isOver(realMillis));

        List<PresetRegionScene> presets = new ArrayList<>();

        for (Map<String, LiveScene> liveScenes : List.of(scenes, flashes)) {
            for (int sceneIndex = projectScenes.size() - 1; sceneIndex >= 0; sceneIndex--) {
                LiveScene liveScene = liveScenes.get(projectScenes.get(sceneIndex).getUuid());

                if (liveScene == null) {
                    continue;
                }

                double sceneLevel = liveScene.level(realMillis) * liveScene.scene.getDimmer() * dimmers.getOrDefault(liveScene.scene.getUuid(), 1.0);
                List<Preset> presetsOfScene = scenePresets.apply(liveScene.scene);

                for (int presetIndex = presetsOfScene.size() - 1; presetIndex >= 0; presetIndex--) {
                    Preset preset = presetsOfScene.get(presetIndex);
                    LivePreset livePreset = new LivePreset(preset, liveScene);
                    livePreset.sceneLevel = sceneLevel;
                    livePreset.beats = beats - liveScene.startBeats;

                    // a preset with times of its own only plays within them, from the moment the scene was picked
                    if (DefaultDesignerService.getPresetPlayStartMillis(preset, liveScene.region, liveScene.plain) <= timeMillis
                            && DefaultDesignerService.getPresetPlayEndMillis(preset, liveScene.region, liveScene.plain) >= timeMillis) {
                        presets.add(livePreset);
                    }
                }
            }
        }

        return presets;
    }
}
