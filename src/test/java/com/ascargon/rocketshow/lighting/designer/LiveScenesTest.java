package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scenes picked by hand on the Play screen while they play: their fades in real time, the clock their presets run
 * on and the scenes flashed on top of them.
 */
class LiveScenesTest {

    private LiveScenes liveScenes;
    private Scene red;
    private Scene blue;
    private Preset redPreset;
    private Preset bluePreset;

    private static Scene scene(String uuid, double fadeInMillis, double fadeOutMillis) {
        Scene scene = new Scene();
        scene.setUuid(uuid);
        scene.setPresetUuids(new String[]{uuid + "-preset"});
        scene.setFadeInMillis(fadeInMillis);
        scene.setFadeOutMillis(fadeOutMillis);
        return scene;
    }

    private static Preset preset(String uuid) {
        Preset preset = new Preset();
        preset.setUuid(uuid);
        return preset;
    }

    @BeforeEach
    void setUp() {
        liveScenes = new LiveScenes();
        red = scene("red", 1000, 2000);
        blue = scene("blue", 0, 0);
        redPreset = preset("red-preset");
        bluePreset = preset("blue-preset");
    }

    private List<PresetRegionScene> presets(double realMillis) {
        double clock = liveScenes.advance(realMillis);
        return liveScenes.getPresets(List.of(red, blue), scene -> List.of(scene == red ? redPreset : bluePreset), clock, realMillis);
    }

    // how much of the scene reaches the stage at the passed real time (0 = it doesn't play)
    private double level(Scene scene, double realMillis) {
        for (PresetRegionScene preset : presets(realMillis)) {
            if (preset.getScene() == scene) {
                return ((LiveScenes.LivePreset) preset).getIntensity(liveScenes.advance(realMillis));
            }
        }

        return 0;
    }

    @Test
    void fadesASceneInWhenItIsPickedAndOutWhenItIsLetGo() {
        liveScenes.setScenes(List.of(red), List.of(), 10000);

        assertEquals(0, level(red, 10000), 1e-9);
        assertEquals(0.5, level(red, 10500), 1e-9);
        assertEquals(1, level(red, 11000), 1e-9);
        assertEquals(1, level(red, 15000), 1e-9);

        liveScenes.setScenes(List.of(), List.of(), 20000);
        assertEquals(0.75, level(red, 20500), 1e-9);
        assertFalse(liveScenes.isIdle(21000));

        // faded out: gone
        assertEquals(0, level(red, 22000), 1e-9);
        assertTrue(liveScenes.isIdle(22000));
    }

    @Test
    void fadesOutFromWhereItGotToWhenItIsLetGoWhileItFadesIn() {
        liveScenes.setScenes(List.of(red), List.of(), 0);
        liveScenes.setScenes(List.of(), List.of(), 250);

        // a quarter in, half of the fade out over
        assertEquals(0.125, level(red, 1250), 1e-9);
    }

    @Test
    void fadesInFromWhereItIsWhenItIsPickedAgainWhileItFadesOut() {
        liveScenes.setScenes(List.of(red), List.of(), 0);
        liveScenes.setScenes(List.of(), List.of(), 5000);

        // halfway out
        liveScenes.setScenes(List.of(red), List.of(), 6000);
        assertEquals(0.5, level(red, 6000), 1e-6);
        assertEquals(0.75, level(red, 6250), 1e-6);
        assertEquals(1, level(red, 6500), 1e-6);
    }

    @Test
    void switchesAtOnceBetweenScenesWithoutFades() {
        liveScenes.setScenes(List.of(blue), List.of(), 0);
        assertEquals(1, level(blue, 0), 1e-9);

        liveScenes.setScenes(List.of(red), List.of(), 1000);
        assertEquals(0, level(blue, 1000), 1e-9);
        assertTrue(presets(1000).stream().noneMatch(preset -> preset.getScene() == blue));
    }

    @Test
    void layersTheScenesLikeTheDesignerWithTheScenesFlashedOnTop() {
        liveScenes.setScenes(List.of(red, blue), List.of(), 0);
        // the first scene of the project is the topmost: processed last
        assertEquals(List.of(blue, red), presets(5000).stream().map(PresetRegionScene::getScene).toList());

        liveScenes.setScenes(List.of(red), List.of(blue), 6000);
        assertEquals(List.of(red, blue), presets(6000).stream().map(PresetRegionScene::getScene).toList());
    }

    @Test
    void flashesASceneAtOnceAndTurnsItOffAtOnce() {
        liveScenes.setScenes(List.of(), List.of(red), 0);

        // without its fades
        assertEquals(1, level(red, 0), 1e-9);

        liveScenes.setScenes(List.of(), List.of(), 100);
        assertEquals(0, level(red, 100), 1e-9);
        assertTrue(liveScenes.isIdle(100));
    }

    @Test
    void dimsTheScenesWithTheirDimmersOfThePlayScreen() {
        blue.setDimmer(0.5);
        liveScenes.setDimmers(Map.of("blue", 0.5));
        liveScenes.setScenes(List.of(blue), List.of(), 0);

        assertEquals(0.25, level(blue, 0), 1e-9);
    }

    @Test
    void playsThePresetsOfASceneFromTheBeatItWasPickedOn() {
        // at 120 BPM a beat lasts 500 ms
        liveScenes.advance(0);
        liveScenes.setScenes(List.of(blue), List.of(), 1234);

        assertEquals(1000, presets(1234).getFirst().getRegion().getStartMillis(), 1e-9);
    }

    @Test
    void playsAPresetWithTimesOfItsOwnWithinThemFromTheMomentTheSceneWasPicked() {
        bluePreset.setStartMillis(2000.0);
        bluePreset.setEndMillis(3000.0);
        liveScenes.advance(0);
        liveScenes.setScenes(List.of(blue), List.of(), 0);

        assertTrue(presets(1000).isEmpty());
        assertEquals(1, presets(2500).size());
        assertTrue(presets(3500).isEmpty());
    }

    @Test
    void runsTheClockAsFastAsTheSpeedMasterAndTheTempoPlayedLive() {
        liveScenes.advance(0);
        assertEquals(1000, liveScenes.advance(1000), 1e-9);

        liveScenes.setSpeed(2, 1000);
        assertEquals(3000, liveScenes.advance(2000), 1e-9);

        // 150 BPM played to curves written for the 120 of the project: a quarter faster
        liveScenes.setSpeed(1, 2000);
        liveScenes.setReferenceBeatsPerMinute(120.0, 2000);
        liveScenes.setBeatsPerMinute(150.0, 2000);
        assertEquals(1.25, liveScenes.getRate(), 1e-9);
        assertEquals(4250, liveScenes.advance(3000), 1e-9);

        // back to the tempo of the project
        liveScenes.setBeatsPerMinute(null, 3000);
        assertEquals(1, liveScenes.getRate(), 1e-9);
    }

    @Test
    void letsABeatFallOnTheMomentTapped() {
        liveScenes.advance(0);
        liveScenes.advance(1300);

        // the closest beat at 120 BPM
        liveScenes.alignBeat(1300);
        assertEquals(1500, liveScenes.advance(1300), 1e-9);
    }

    @Test
    void startsAnew() {
        liveScenes.advance(0);
        liveScenes.setScenes(List.of(blue), List.of(), 500);
        liveScenes.reset();

        assertTrue(liveScenes.isIdle(500));
        assertEquals(0, liveScenes.advance(800), 1e-9);
    }
}
