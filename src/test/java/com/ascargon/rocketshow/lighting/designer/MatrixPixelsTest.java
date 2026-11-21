package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pixels of a fixture matrix, like the Open Fixture Library defines them, which the migration of projects of
 * version 1 lists the fixtures of a preset with (see ProjectMigration): the pixel keys are given by Z, Y and X, the
 * positions count from 1, the channels of a mode are repeated for pixels and pixel groups. These mirror FixtureService
 * in the Rocket Show Designer.
 */
class MatrixPixelsTest {

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

    private final FixtureProfile profile = new ObjectMapper().readValue(PROFILE, FixtureProfile.class);

    private static String describe(ProjectMigration.MatrixPixel pixel) {
        return pixel.key() + " " + pixel.x() + "/" + pixel.y() + "/" + pixel.z();
    }

    private static List<String> describe(FixtureProfile profile) {
        return ProjectMigration.getAllPixels(profile).stream().map(MatrixPixelsTest::describe).toList();
    }

    private static FixtureProfile profile(String matrix) {
        return new ObjectMapper().readValue("{\"matrix\": " + matrix + "}", FixtureProfile.class);
    }

    private static List<String> keys(FixtureProfile profile, String... repeatFor) {
        return ProjectMigration.getPixelsInOrder(profile, List.of(repeatFor)).stream().map(ProjectMigration.MatrixPixel::key).toList();
    }

    @Test
    void placesEachPixelAtItsPosition() {
        assertEquals(List.of("A1 1/1/1", "A2 2/1/1", "B1 1/2/1", "B2 2/2/1", "C1 1/1/2", "D1 1/2/2", "D2 2/2/2"),
                describe(profile));
    }

    @Test
    void namesThePixelsOfAPixelCountLikeTheLibrary() {
        assertEquals(List.of("1 1/1/1", "2 2/1/1", "3 3/1/1"),
                describe(profile("{\"pixelCount\": [3, 1, 1]}")));
        assertEquals(List.of("1", "2"), keys(profile("{\"pixelCount\": [1, 2, 1]}"), "eachPixelXYZ"));
        assertEquals(List.of("(1, 1) 1/1/1", "(2, 1) 2/1/1", "(1, 2) 1/2/1", "(2, 2) 2/2/1"),
                describe(profile("{\"pixelCount\": [2, 2, 1]}")));
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

        // only the digits 0 to 9 are numbers
        assertTrue(ProjectMigration.compareKeys("2", "10") < 0);
        assertTrue(ProjectMigration.compareKeys("٢", "10") > 0);
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

}
