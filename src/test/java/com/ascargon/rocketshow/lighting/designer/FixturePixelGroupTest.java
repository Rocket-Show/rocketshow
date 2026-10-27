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
 * The pixels of a fixture matrix and its pixel groups, like the Open Fixture Library defines them: the pixel keys are
 * given by Z, Y and X, the positions count from 1. These mirror FixtureService and FixturePixelGroupResolveService in
 * the Rocket Show Designer.
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
                  "Corners": ["D2", "A1"],
                  "Second row": {"y": ["=2"]},
                  "Odd": {"x": ["odd"]},
                  "Even": {"x": ["even"]},
                  "First layer of B": {"z": ["<=1"], "name": ["^B"]},
                  "Second layer": {"z": [">=2"]},
                  "Every second": {"x": ["2n"]},
                  "Plain number": {"y": ["1"], "z": ["2"]}
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

    private static FixtureProfile profile(String matrix) {
        return new ObjectMapper().readValue("{\"matrix\": " + matrix + "}", FixtureProfile.class);
    }

    private List<String> keys(FixtureProfile profile, String... repeatFor) {
        return designerService.getPixelsInOrder(profile, List.of(repeatFor)).stream().map(CachedFixturePixel::getKey).toList();
    }

    @Test
    void placesEachPixelAtItsPosition() {
        assertEquals(List.of("A1 1/1/1", "A2 2/1/1", "B1 1/2/1", "B2 2/2/1", "C1 1/1/2", "D1 1/2/2", "D2 2/2/2"),
                designerService.getAllPixelKeys(profile).stream().map(FixturePixelGroupTest::describe).toList());
    }

    @Test
    void namesThePixelsOfAPixelCountLikeTheLibrary() {
        assertEquals(List.of("1 1/1/1", "2 2/1/1", "3 3/1/1"),
                designerService.getAllPixelKeys(profile("{\"pixelCount\": [3, 1, 1]}")).stream().map(FixturePixelGroupTest::describe).toList());
        assertEquals(List.of("1", "2"), keys(profile("{\"pixelCount\": [1, 2, 1]}"), "eachPixelXYZ"));
        assertEquals(List.of("(1, 1) 1/1/1", "(2, 1) 2/1/1", "(1, 2) 1/2/1", "(2, 2) 2/2/1"),
                designerService.getAllPixelKeys(profile("{\"pixelCount\": [2, 2, 1]}")).stream().map(FixturePixelGroupTest::describe).toList());
        // X and Z
        assertEquals(List.of("(1, 1)", "(2, 1)", "(1, 2)", "(2, 2)"), keys(profile("{\"pixelCount\": [2, 1, 2]}"), "eachPixelXYZ"));
        assertEquals(List.of("(1, 1, 1)", "(2, 1, 1)", "(1, 2, 1)", "(2, 2, 1)", "(1, 1, 2)", "(2, 1, 2)", "(1, 2, 2)", "(2, 2, 2)"),
                keys(profile("{\"pixelCount\": [2, 2, 2]}"), "eachPixelXYZ"));
    }

    @Test
    void ordersThePixelsByTheirAxes() {
        // The first axis changes fastest
        assertEquals(List.of("A1", "A2", "B1", "B2", "C1", "D1", "D2"), keys(profile, "eachPixelXYZ"));
        assertEquals(List.of("A1", "B1", "A2", "B2", "C1", "D1", "D2"), keys(profile, "eachPixelYXZ"));
        assertEquals(List.of("A1", "C1", "B1", "D1", "A2", "B2", "D2"), keys(profile, "eachPixelZYX"));
        assertEquals(List.of("A1", "C1", "A2", "B1", "D1", "B2", "D2"), keys(profile, "eachPixelZXY"));
        assertEquals(List.of("A1", "A2", "C1", "B1", "B2", "D1", "D2"), keys(profile, "eachPixelXZY"));
        assertEquals(List.of("A1", "B1", "C1", "D1", "A2", "B2", "D2"), keys(profile, "eachPixelYZX"));

        FixtureProfile grid = profile("{\"pixelCount\": [3, 2, 1]}");
        assertEquals(List.of("(1, 1)", "(1, 2)", "(2, 1)", "(2, 2)", "(3, 1)", "(3, 2)"), keys(grid, "eachPixelYXZ"));
    }

    @Test
    void ordersThePixelKeysByTheirNumbers() {
        // 2 before 10, like the fixture library
        FixtureProfile bar = profile("{\"pixelKeys\": [[[\"10\", \"2\", \"1\", \"Spot\", \"11\"]]]}");

        assertEquals(List.of("1", "2", "10", "11", "Spot"), keys(bar, "eachPixelABC"));
    }

    @Test
    void repeatsChannelsForPixelGroupsAsAWhole() {
        // E.g. "Red Corners" controls both corners
        assertEquals(List.of("Corners", "Second row"), keys(profile, "Corners", "Second row"));
        assertEquals(List.of("All", "Corners", "Second row", "Odd", "Even", "First layer of B", "Second layer", "Every second", "Plain number"),
                keys(profile, "eachPixelGroup"));
        // Pixels and groups mixed, also a single one
        assertEquals(List.of("D2", "Corners"), keys(profile, "D2", "Corners"));
        assertEquals(List.of("B1"), keys(profile, "B1"));
        assertEquals(List.of(), keys(profile, "No such pixel"));
    }

    @Test
    void resolvesGroupsByKeys() {
        // All alphanumerically, listed keys as listed
        assertEquals(List.of("A1", "A2", "B1", "B2", "C1", "D1", "D2"), group("All"));
        assertEquals(List.of("D2", "A1"), group("Corners"));
    }

    @Test
    void resolvesGroupsByPosition() {
        assertEquals(List.of("B1", "B2", "D1", "D2"), group("Second row"));
        assertEquals(List.of("A1", "B1", "C1", "D1"), group("Odd"));
        assertEquals(List.of("A2", "B2", "D2"), group("Even"));
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
