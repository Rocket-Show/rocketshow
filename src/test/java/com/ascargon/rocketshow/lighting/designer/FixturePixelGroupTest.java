package com.ascargon.rocketshow.lighting.designer;

import com.ascargon.rocketshow.lighting.LightingService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.FileFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The pixels of a fixture matrix and its pixel groups. These mirror FixtureService and
 * FixturePixelGroupResolveService in the Rocket Show Designer, which read the pixel keys as
 * [x][y][z] and count the positions from 0.
 */
class FixturePixelGroupTest {

    // Two layers of two rows with two pixels each, one of them missing
    private static final String PROFILE = """
            {
              "matrix": {
                "pixelKeys": [
                  [["A1", "A2"], ["B1", "B2"]],
                  [["C1", null], ["D1", "D2"]]
                ],
                "pixelGroups": {
                  "All": "all",
                  "Corners": ["A1", "D2"],
                  "Second row": {"y": ["=1"]},
                  "Odd": {"z": ["odd"]},
                  "Even": {"z": ["even"]},
                  "First layer of B": {"x": ["<=0"], "name": ["^B"]},
                  "Second layer": {"x": [">=1"]},
                  "Every second": {"z": ["2n+1"]},
                  "Plain number": {"y": ["0"], "x": ["1"]}
                }
              }
            }
            """;

    private FixtureProfile profile;
    private DefaultDesignerService designerService;
    private final DefaultFixturePixelGroupResolveService resolveService = new DefaultFixturePixelGroupResolveService();

    @BeforeEach
    void setUp() {
        profile = new ObjectMapper().readValue(PROFILE, FixtureProfile.class);

        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(new Settings());
        designerService = new DefaultDesignerService(settingsService, mock(FileFilterService.class), mock(LightingService.class), resolveService);
    }

    private static String describe(CachedFixturePixel pixel) {
        return pixel.getKey() + " " + pixel.getX() + "/" + pixel.getY() + "/" + pixel.getZ();
    }

    private List<String> group(String name) {
        return resolveService.getPixelsForKeyOrGroup(profile.getMatrix(), designerService.getAllPixelKeys(profile), name).stream()
                .map(CachedFixturePixel::getKey)
                .toList();
    }

    @Test
    void placesEachPixelAtItsPosition() {
        assertEquals(List.of("A1 0/0/0", "A2 0/0/1", "B1 0/1/0", "B2 0/1/1", "C1 1/0/0", "D1 1/1/0", "D2 1/1/1"),
                designerService.getAllPixelKeys(profile).stream().map(FixturePixelGroupTest::describe).toList());
    }

    @Test
    void resolvesGroupsByKeys() {
        assertEquals(List.of("A1", "A2", "B1", "B2", "C1", "D1", "D2"), group("All"));
        assertEquals(List.of("A1", "D2"), group("Corners"));
    }

    @Test
    void resolvesGroupsByPosition() {
        assertEquals(List.of("B1", "B2", "D1", "D2"), group("Second row"));
        assertEquals(List.of("A2", "B2", "D2"), group("Odd"));
        assertEquals(List.of("A1", "B1", "C1", "D1"), group("Even"));
        assertEquals(List.of("C1", "D1", "D2"), group("Second layer"));
        assertEquals(List.of("A2", "B2", "D2"), group("Every second"));
        assertEquals(List.of("C1"), group("Plain number"));
    }

    @Test
    void combinesAllConstraintsOfAGroup() {
        assertEquals(List.of("B1", "B2"), group("First layer of B"));
    }

    @Test
    void prefersAPixelKeyOverAGroup() {
        assertEquals(List.of("B2"), group("B2"));
        assertEquals(List.of(), group("No such group"));
    }

    @Test
    void rejectsUnknownRules() {
        FixtureMatrixPixelGroup group = new FixtureMatrixPixelGroup();
        group.getConstraints().setX(List.of("every third"));

        assertThrows(IllegalArgumentException.class, () -> resolveService.resolveGroupPixels(group, designerService.getAllPixelKeys(profile)));
    }

}
