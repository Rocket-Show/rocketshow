package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.TestSettings;
import com.ascargon.rocketshow.util.DefaultFileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The hold points of the timeline (where a composition waits for GO while the lights run on), as the engine keeps them
 * for a composition playing and for the live preview of the designer, and the tempo and the bars it plays in.
 */
class DesignerHoldsTest {

    @TempDir
    Path directory;

    private DefaultDesignerService designerService;

    // a song in 3/4 at 90 BPM, with two hold points and a label between them
    private static final String PROJECT = """
            {
              "version": 10,
              "name": "Show",
              "selectedCompositionUuid": "song",
              "fixtureProfiles": [],
              "fixtures": [],
              "presets": [],
              "scenes": [],
              "sceneFolders": [],
              "compositions": [{
                "uuid": "song",
                "name": "Song",
                "syncType": "none",
                "beatsPerMinute": 90,
                "timeSignatureUpper": 3,
                "scenePlaybackRegions": [],
                "markers": [
                  {"uuid": "end", "name": "End", "positionMillis": 60000, "type": "hold"},
                  {"uuid": "verse", "name": "Verse", "positionMillis": 30000, "type": "label"},
                  {"uuid": "intro", "name": "Intro", "positionMillis": 20000, "type": "hold"}
                ]
              }]
            }
            """;

    @BeforeEach
    void setUp() {
        designerService = new DefaultDesignerService(TestSettings.inDirectory(directory), new DefaultFileFilterService(), mock(LightingService.class), new DefaultFixturePixelGroupResolveService());
    }

    @Test
    void findsTheHoldPointsOfAComposition() throws Exception {
        designerService.saveProject(PROJECT);

        assertEquals(List.of(20000.0, 60000.0), designerService.getHoldPositions("Song"));
        assertEquals(List.of(), designerService.getHoldPositions("Another song"));
        assertEquals(List.of(), designerService.getHoldPositions(null));
    }

    @Test
    void holdsUntilGo() {
        assertFalse(designerService.isHolding());

        designerService.hold(20000);
        assertTrue(designerService.isHolding());

        designerService.endHold();
        assertFalse(designerService.isHolding());
        designerService.endHold();
        assertFalse(designerService.isHolding());

        // jumped back before the hold: it wasn't gone through anymore
        designerService.hold(20000);
        designerService.forgetHolds(0);
        assertFalse(designerService.isHolding());
    }

    @Test
    void holdsLikeTheTimelineOfTheDesigner() {
        List<CompositionHold> holds = List.of(new CompositionHold(20000, 1500));

        designerService.setPreviewHolds(holds, true);
        assertTrue(designerService.isHolding());

        // the designer's timeline went on from there
        designerService.setPreviewHolds(holds, false);
        assertFalse(designerService.isHolding());

        // standing at no hold point
        designerService.setPreviewHolds(List.of(), true);
        assertFalse(designerService.isHolding());
        designerService.setPreviewHolds(null, true);
        assertFalse(designerService.isHolding());

        // the holds of the designer are its own: the engine changes copies of them
        designerService.setPreviewHolds(holds, true);
        designerService.endHold();
        assertEquals(1500, holds.getFirst().getHeldMillis());
    }

    @Test
    void playsInTheBarsOfTheCompositionOpen() throws Exception {
        assertEquals(4, designerService.getBeatsPerBar());

        designerService.saveProject(PROJECT);
        designerService.load(null, designerService.getProjectByName("Show"), null);

        assertEquals(3, designerService.getBeatsPerBar());
        // nothing plays yet: no tempo is programmed
        assertNull(designerService.getProgrammedBeatsPerMinute());
    }

    @Test
    void runsTheBeatClockAtTheTempoFromOutside() {
        designerService.setOutsideBeatsPerMinute(140.0);

        assertEquals(140.0, designerService.getOutsideBeatsPerMinute());
        assertEquals(140.0, designerService.getTempoBeatsPerMinute());
        assertEquals(140.0, designerService.getBeatClock().getBeatsPerMinute());
    }

}
