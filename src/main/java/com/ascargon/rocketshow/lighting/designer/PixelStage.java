package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Where the pixels of the fixtures are on the stage, for a pixel effect spread across it: each LED on the front of the
 * housing of its fixture (like the 3D preview of the designer shows it), the housing where the fixture hangs. Seen from
 * the front or from above, the LEDs of a preset make up one picture. The designer places them the very same way
 * (pixel-stage.ts).
 *
 * @author Moritz A. Vieli
 */
final class PixelStage {

    // the room a pixel takes on a fixture which does not say how large it is, and how deep such a fixture is (in cm)
    static final double DEFAULT_PIXEL_PITCH_CM = 8;
    static final double DEFAULT_PIXEL_DEPTH_CM = 7;

    // how far the top of the housing of a fixture hanging from the truss is above where its group is placed, in cm
    static final double PIXEL_HOUSING_TOP_CM = 8;

    private PixelStage() {
    }

    // The width, height and depth of the housing of a fixture with pixels (in cm): as large as its profile says (in
    // mm), or as large as its pixels need
    static double[] housingSize(FixtureProfile profile, int columns, int rows) {
        double[] dimensions = profile.getPhysical() == null || profile.getPhysical().getDimensions() == null ? new double[0] : profile.getPhysical().getDimensions();

        return new double[]{
                orElse(dimension(dimensions, 0), columns * DEFAULT_PIXEL_PITCH_CM),
                orElse(dimension(dimensions, 1), rows * DEFAULT_PIXEL_PITCH_CM),
                orElse(dimension(dimensions, 2), DEFAULT_PIXEL_DEPTH_CM)
        };
    }

    private static double dimension(double[] dimensions, int index) {
        return index < dimensions.length ? dimensions[index] / 10 : 0;
    }

    // a size the profile does not give (or gives as 0) is the one the pixels need, like in the designer
    private static double orElse(double value, double otherwise) {
        return value == 0 || Double.isNaN(value) ? otherwise : value;
    }

    // where the housing is below the place its fixture is fastened at (in cm, y up): below the truss, on the floor
    // above it (the fixture is turned upside down there, the housing not), in the middle of a fixture placed by hand
    static double housingOffset(String positioning, double height) {
        return "manual".equals(positioning) ? 0 : PIXEL_HOUSING_TOP_CM - height / 2;
    }

    static boolean isOnTheFloor(String positioning) {
        return "bottomFront".equals(positioning) || "bottomBack".equals(positioning);
    }

    // Where the LED of a part of a fixture (at its place in the fixture, see DefaultDesignerService.getPixelPlace) is on
    // the stage, in cm: on the front of the housing, the housing as the fixture hangs
    static double[] position(PixelContext place, double[] size, PanTilt.StagePlacement placement, String positioning) {
        double x = (place.x - 0.5) * size[0];
        double y = (0.5 - place.y) * size[1];
        double z = size[2] / 2;
        double offset = housingOffset(positioning, size[1]);

        // in the frame of the fixture, which is turned upside down on the floor
        double[] local = isOnTheFloor(positioning) ? new double[]{x, offset - y, -z} : new double[]{x, offset + y, z};
        double[][] matrix = PanTilt.getRotationMatrix(placement.rotation());
        double[] position = new double[3];

        for (int row = 0; row < 3; row++) {
            position[row] = placement.position()[row] + matrix[row][0] * local[0] + matrix[row][1] * local[1] + matrix[row][2] * local[2];
        }

        return position;
    }

    // a coordinate rounded to a millimeter, which tells the columns and rows of the picture apart
    private static long millimeters(double value) {
        return (long) Math.floor(value * 10 + 0.5);
    }

    // The places of the LEDs in the picture they make up together, seen from the 'front' (x to the right, y up) or from
    // above ('top', the back of the stage up): the picture framed around them, each one in a column and a row of its
    // own, the columns being the places they are at from the left (a millimeter apart at least) and the rows the ones
    // from the top. A picture with one column (or row) has it in the middle. The aspect is the one of the picture, a
    // pixel more on each axis.
    static List<PixelContext> places(List<double[]> positions, String view) {
        List<double[]> points = new ArrayList<>();
        TreeSet<Long> columnValues = new TreeSet<>();
        TreeSet<Long> rowValues = new TreeSet<>();
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (double[] position : positions) {
            double[] point = "top".equals(view) ? new double[]{position[0], -position[2]} : new double[]{position[0], position[1]};
            points.add(point);
            columnValues.add(millimeters(point[0]));
            rowValues.add(-millimeters(point[1]));
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }

        Map<Long, Integer> columnOf = indices(columnValues);
        Map<Long, Integer> rowOf = indices(rowValues);
        int columns = columnValues.size();
        int rows = rowValues.size();
        double width = columns > 1 ? (maxX - minX) * columns / (columns - 1) : 0;
        double height = rows > 1 ? (maxY - minY) * rows / (rows - 1) : 0;
        double longest = Math.max(width, height);
        List<PixelContext> places = new ArrayList<>();

        for (double[] point : points) {
            PixelContext context = new PixelContext();
            context.column = columnOf.get(millimeters(point[0]));
            context.row = rowOf.get(-millimeters(point[1]));
            context.columns = columns;
            context.rows = rows;
            context.x = columns > 1 ? (0.5 + ((point[0] - minX) / (maxX - minX)) * (columns - 1)) / columns : 0.5;
            context.y = rows > 1 ? (0.5 + ((maxY - point[1]) / (maxY - minY)) * (rows - 1)) / rows : 0.5;
            context.aspectX = longest > 0 ? width / longest : 1;
            context.aspectY = longest > 0 ? height / longest : 1;
            places.add(context);
        }

        return places;
    }

    // the place of each of the values, from the lowest to the highest
    private static Map<Long, Integer> indices(TreeSet<Long> values) {
        Map<Long, Integer> indices = new HashMap<>();

        for (Long value : values) {
            indices.put(value, indices.size());
        }

        return indices;
    }

}
