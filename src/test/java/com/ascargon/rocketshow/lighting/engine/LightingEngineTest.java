package com.ascargon.rocketshow.lighting.engine;

import com.ascargon.rocketshow.lighting.LightingUniverseState;
import com.ascargon.rocketshow.lighting.designer.DmxOverride;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The boundary of engine.wasm in the JVM and the wrapper playing a project with it (DesignerEngine). The tests needing
 * engine.wasm are skipped without it (see testing/build-lighting-engine.sh).
 */
class LightingEngineTest {

    // two dimmers on a universe, one scene lighting them, a composition playing it from 1 s on
    private static final String PROJECT = """
            {"version": 11,
             "fixtureProfiles": [{"uuid": "dimmer", "name": "Dimmer", "availableChannels": {"Dimmer": {"capability": {"type": "Intensity"}}},
               "modes": [{"name": "1-channel", "shortName": "1ch", "channels": ["Dimmer"]}]}],
             "fixtures": [{"uuid": "a", "name": "A", "profileUuid": "dimmer", "modeShortName": "1ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 0},
                          {"uuid": "b", "name": "B", "profileUuid": "dimmer", "modeShortName": "1ch", "dmxUniverseUuid": "universe", "dmxFirstChannel": 1}],
             "presetFixtures": [{"fixtureUuid": "a"}, {"fixtureUuid": "b"}],
             "presets": [{"uuid": "half", "fixtures": [{"fixtureUuid": "a"}, {"fixtureUuid": "b"}],
               "steps": [{"uuid": "step", "fixtureCapabilityValues": [{"type": "Intensity", "valuePercentage": 0.5}]}]}],
             "scenes": [{"uuid": "scene", "presetUuids": ["half"]}],
             "compositions": [{"uuid": "song", "name": "Song", "tracks": [{"uuid": "track"}],
               "scenePlaybackRegions": [{"sceneUuid": "scene", "trackUuid": "track", "startMillis": 1000, "endMillis": 5000}]}]}
            """;

    @Test
    void findsTheNumbersOfTheUuids() {
        ShowIndex index = new ShowIndex(List.of("a", "b", "a"), null, List.of("p"), List.of(List.of("s1", "s2")), List.of("scene"),
                List.of(new ShowIndex.CompositionName("c", "Song")), List.of("universe"));

        assertEquals(List.of(0, 2), index.fixtureNumbers("a"));
        assertEquals(List.of(), index.fixtureNumbers("x"));
        assertEquals(0, index.preset("p"));
        assertEquals(-1, index.preset(null));
        assertEquals(1, index.step(0, "s2"));
        assertEquals(-1, index.step(-1, "s2"));
        assertEquals(-1, index.step(3, "s2"));
        assertEquals(0, index.scene("scene"));
        assertEquals(0, index.compositionNamed("Song"));
        assertEquals(-1, index.compositionNamed("Other"));
        assertEquals(0, index.universe("universe"));
        assertEquals(-1, index.universe(null));
    }

    @Test
    void knowsWhatItIs() {
        assumeTrue(LightingEngine.isAvailable());
        LightingEngine engine = LightingEngine.loadPacked();

        assertEquals(1, engine.formatVersion() >> 16, "the major version of the compiled show format");
        assertTrue(engine.behaviourVersion() > 0);
        assertNotEquals(0, engine.features());
        assertEquals(40, LightingEngine.packedRevision().length());
    }

    @Test
    void saysWhyItCannotCompileAProject() {
        assumeTrue(LightingEngine.isAvailable());
        CompiledShow compiled = LightingEngine.loadPacked().compile("{\"version\": 11, \"fixtures\": 7}");

        assertEquals(CompiledShow.UNREADABLE, compiled.code());
        assertFalse(compiled.message().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new DesignerEngine(LightingEngine.loadPacked(), "{\"version\": 11, \"fixtures\": 7}", 0));
    }

    @Test
    void playsTheTimelineIntoTheUniverses() {
        assumeTrue(LightingEngine.isAvailable());

        try (DesignerEngine engine = new DesignerEngine(LightingEngine.loadPacked(), PROJECT, 1000)) {
            assertTrue(engine.playTimeline("Song", true, false));
            assertFalse(engine.playTimeline("Other", true, false));
            assertTrue(engine.playTimeline("Song", true, false));

            engine.setPosition(1000, 500, false);
            engine.render(1000);
            assertEquals(0, engine.getValue("universe", 0));

            engine.setPosition(2000, 2000, false);
            engine.render(2000);
            assertEquals(127, engine.getValue("universe", 0));
            assertEquals(127, engine.getValue("universe", 1));
            assertEquals(-1, engine.getValue("other", 0));

            // the master dimmer of the Play screen, a fader of the designer and a fixture identified
            engine.setMasterDimmer(0.5);
            DmxOverride fader = new DmxOverride();
            fader.setUniverseUuid("universe");
            fader.setDmxChannel(10);
            fader.setValue(300);
            engine.setDmxOverrides(List.of(fader));
            engine.setIdentifyFixtures(List.of("b"));
            engine.render(2000);
            assertEquals(63, engine.getValue("universe", 0));
            assertEquals(255, engine.getValue("universe", 10));
            assertEquals(127, engine.getValue("universe", 1), "blinking at full (lit at first), dimmed by the master dimmer");

            engine.setDmxOverrides(List.of());
            engine.setIdentifyFixtures(List.of());
            engine.render(2000);
            assertEquals(0, engine.getValue("universe", 10));
            assertEquals(63, engine.getValue("universe", 1));

            // written to the universes mapped to the one of the project, the first of them
            LightingUniverseState mapped = new LightingUniverseState();
            mapped.setMappingUuid("universe");
            LightingUniverseState twice = new LightingUniverseState();
            twice.setMappingUuid("universe");
            LightingUniverseState other = new LightingUniverseState();
            other.setMappingUuid("other");
            other.getUniverse().put(0, 99);
            new EngineRenderer.Rendered(0, 2000, true, engine.copyUniverses(), engine.getUniverseOffsets()).writeTo(List.of(mapped, twice, other));

            assertEquals(63, mapped.getUniverse().get(0));
            assertEquals(512, mapped.getUniverse().size());
            assertTrue(twice.getUniverse().isEmpty());
            assertTrue(other.getUniverse().isEmpty());
        }
    }

    @Test
    void turnsAFaultOfTheEngineIntoAnException() {
        assumeTrue(LightingEngine.isAvailable());
        LightingEngine engine = LightingEngine.loadPacked();

        // an engine which isn't one: the module traps reading memory it hasn't
        EngineShow broken = new EngineShow(engine, 0x7fff_fff0, new ShowIndex(null, null, null, null, null, null, null));
        assertThrows(RuntimeException.class, () -> broken.render(0));

        // a new instance plays
        try (DesignerEngine fresh = new DesignerEngine(LightingEngine.loadPacked(), PROJECT, 0)) {
            fresh.previewScenes(null, null, List.of("scene"));
            fresh.render(0);
            assertEquals(127, fresh.getValue("universe", 0));
        }
    }

}
