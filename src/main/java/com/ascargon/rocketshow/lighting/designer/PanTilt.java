package com.ascargon.rocketshow.lighting.designer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a moving head points is set in degrees from the middle of what it reaches on each axis (pan and tilt), which
 * points every model the same way: a head reaching 540° and one reaching 630° turn by the same angle, as far as they
 * can. The fixtures of a preset can be fanned out over a number of degrees, or point at a spot on the stage from
 * wherever they hang. The designer plays them the very same way (pan-tilt.ts), with the same sines (CycleSine) and arc
 * tangents (ArcTangent).
 */
final class PanTilt {

    // what a moving head reaches on an axis, if its profile doesn't say (like in the 3D preview of the designer)
    static final double DEFAULT_PAN_RANGE_DEGREES = 540;
    static final double DEFAULT_TILT_RANGE_DEGREES = 270;

    // where the head of a moving head turns, below the place it is fastened at
    private static final double HEAD_OFFSET_CM = 5.4;

    /**
     * Where a fixture hangs: its place on the stage (in cm, y up) and how it is turned (in degrees, x, y and z one
     * after the other, like in the 3D preview).
     */
    record StagePlacement(double[] position, double[] rotation) {
    }

    /**
     * What places a fixture of a preset at a spot of its own: its place in the chase of the preset (to fan it out) and
     * where it hangs (to aim it at a spot, null if it isn't known).
     */
    record Context(int fixtureIndex, int fixtureCount, StagePlacement placement) {
    }

    /**
     * Where a moving head points, in degrees from the middle of its ranges.
     */
    record Aim(double pan, double tilt) {
    }

    private PanTilt() {
    }

    static boolean isPanTilt(FixtureCapability.FixtureCapabilityType type) {
        return type == FixtureCapability.FixtureCapabilityType.Pan || type == FixtureCapability.FixtureCapabilityType.Tilt;
    }

    // does the value point the fixtures at a spot on the stage?
    static boolean hasAim(FixtureCapabilityValue value) {
        return isNumber(value.getAimX()) && isNumber(value.getAimY()) && isNumber(value.getAimZ());
    }

    private static boolean isNumber(Double value) {
        return value != null && !value.isNaN();
    }

    private static boolean hasPercentage(FixtureCapabilityValue value) {
        return value.getValuePercentage() != null && value.getValuePercentage() >= 0;
    }

    // The degrees a channel turns its fixture over on the passed axis (from the start to the end of its range, negative
    // for a range running the other way round), or null if the channel doesn't turn it on that axis
    static Double getAxisRange(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        List<CachedFixtureCapability> axisCapabilities = channel.getCapabilities().stream()
                .filter(channelCapability -> channelCapability.getCapability().getType() == type)
                .toList();

        if (axisCapabilities.isEmpty()) {
            return null;
        }

        double fallback = type == FixtureCapability.FixtureCapabilityType.Pan ? DEFAULT_PAN_RANGE_DEGREES : DEFAULT_TILT_RANGE_DEGREES;
        FixtureCapability capability = CapabilityPositions.getWidest(channel, axisCapabilities).getCapability();
        Double start = CapabilityPositions.getPropertyRank(capability.getAngleStart());
        Double end = CapabilityPositions.getPropertyRank(capability.getAngleEnd());
        double range = (end == null ? fallback : end) - (start == null ? 0 : start);

        return range == 0 ? fallback : range;
    }

    // the value of a channel pointing its fixture at the passed degrees from the middle of its range, as far as it
    // reaches
    static double getAxisChannelValue(CachedFixtureChannel channel, double range, double degrees) {
        double position = Math.max(Math.min(0.5 + degrees / range, 1), 0);

        return channel.getMaxValue() * position;
    }

    // the degrees the passed fixture of a preset is turned by to fan the fixtures out: the first one by half of them to
    // one side, the last one by half of them to the other
    static double getSpreadDegrees(Double spreadDegrees, Context context) {
        if (!isNumber(spreadDegrees) || context == null || context.fixtureCount() < 2) {
            return 0;
        }

        return spreadDegrees * ((double) context.fixtureIndex() / (context.fixtureCount() - 1) - 0.5);
    }

    // Where a pan or tilt value points a fixture on its axis, in degrees from the middle of its range, or null if it
    // can't tell (e.g. a spot to aim at, but no idea where the fixture hangs). A spot is where the fixtures point,
    // whatever the mirror of the preset says; everything else is mirrored around the middle.
    static Double getPanTiltDegrees(FixtureCapabilityValue value, double range, boolean mirror, Context context) {
        double spread = getSpreadDegrees(value.getSpreadDegrees(), context);

        if (hasAim(value)) {
            if (context == null || context.placement() == null) {
                return null;
            }

            Aim aim = getAimDegrees(context.placement(), new double[]{value.getAimX(), value.getAimY(), value.getAimZ()});

            return (value.getType() == FixtureCapability.FixtureCapabilityType.Pan ? aim.pan() : aim.tilt()) + spread;
        }

        double degrees;

        if (isNumber(value.getValueDegrees())) {
            degrees = value.getValueDegrees();
        } else if (hasPercentage(value)) {
            degrees = (value.getValuePercentage() - 0.5) * range;
        } else {
            return null;
        }

        degrees += spread;

        return mirror ? -degrees : degrees;
    }

    // The value of a channel playing a pan or tilt value, or null if the channel doesn't turn the fixture on its axis
    // or the value doesn't say where to. A percentage of the range (of a project before the degrees) is played like it
    // always was, unless the fixtures are fanned out.
    static Double getPanTiltChannelValue(FixtureCapabilityValue value, CachedFixtureChannel channel, boolean mirror, Context context) {
        Double range = getAxisRange(channel, value.getType());

        if (range == null) {
            return null;
        }

        if (!hasAim(value) && !isNumber(value.getValueDegrees()) && hasPercentage(value) && getSpreadDegrees(value.getSpreadDegrees(), context) == 0) {
            return channel.getMaxValue() * (mirror ? 1 - value.getValuePercentage() : value.getValuePercentage());
        }

        Double degrees = getPanTiltDegrees(value, range, mirror, context);

        return degrees == null ? null : getAxisChannelValue(channel, range, degrees);
    }

    // Where the passed values of a preset point a fixture of the passed profile on an axis, in degrees from the middle
    // of its range, or null if they don't (a later value wins, like when they are mixed). A movement of the preset runs
    // around it (see EffectPanTilt).
    static Double getPresetPanTiltDegrees(List<FixtureCapabilityValue> values, FixtureCapability.FixtureCapabilityType type, String profileUuid, double range, boolean mirror, Context context) {
        Double degrees = null;

        for (FixtureCapabilityValue value : values) {
            if (value.getType() == type && (value.getProfileUuid() == null || value.getProfileUuid().equals(profileUuid))) {
                Double valueDegrees = getPanTiltDegrees(value, range, mirror, context);

                if (valueDegrees != null) {
                    degrees = valueDegrees;
                }
            }
        }

        return degrees;
    }

    // ---- where the fixtures hang ----

    // the sine and the cosine of an angle in degrees, exact on the quarters, which the fixtures are mostly turned by,
    // and worked out like the designer does in between (see CycleSine)
    private static double sinDegrees(double degrees) {
        if (degrees % 90 == 0) {
            return new double[]{0, 1, 0, -1}[(int) ((((degrees / 90) % 4) + 4) % 4)];
        }

        return CycleSine.sin(degrees / 360);
    }

    private static double cosDegrees(double degrees) {
        if (degrees % 90 == 0) {
            return new double[]{1, 0, -1, 0}[(int) ((((degrees / 90) % 4) + 4) % 4)];
        }

        return CycleSine.sin(degrees / 360 + 0.25);
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                result[row][column] = a[row][0] * b[0][column] + a[row][1] * b[1][column] + a[row][2] * b[2][column];
            }
        }

        return result;
    }

    // the matrix turning a fixture by the passed degrees around x, y and z, one after the other (like the 3D preview)
    static double[][] getRotationMatrix(double[] rotation) {
        double sx = sinDegrees(rotation[0]);
        double cx = cosDegrees(rotation[0]);
        double sy = sinDegrees(rotation[1]);
        double cy = cosDegrees(rotation[1]);
        double sz = sinDegrees(rotation[2]);
        double cz = cosDegrees(rotation[2]);

        double[][] x = {{1, 0, 0}, {0, cx, -sx}, {0, sx, cx}};
        double[][] y = {{cy, 0, sy}, {0, 1, 0}, {-sy, 0, cy}};
        double[][] z = {{cz, -sz, 0}, {sz, cz, 0}, {0, 0, 1}};

        return multiply(multiply(x, y), z);
    }

    private record Row(String positioning, double y, double z, boolean hanging) {
    }

    // Where the fixtures of a project hang: a fixture placed by hand where it was placed, the others spread evenly over
    // the width of the stage, in front or at the back, above or on the floor, in the order of the fixture pool. This is
    // what the 3D preview of the designer shows them at.
    static Map<String, StagePlacement> getStagePlacements(Project project) {
        Map<String, StagePlacement> placements = new HashMap<>();
        Map<String, Fixture> fixtures = new HashMap<>();
        List<Fixture> projectFixtures = project.getFixtures() == null ? List.of() : project.getFixtures();
        List<PresetFixture> presetFixtures = project.getPresetFixtures() == null ? List.of() : project.getPresetFixtures();

        for (Fixture fixture : projectFixtures) {
            fixtures.put(fixture.getUuid(), fixture);

            if ("manual".equals(fixture.getPositioning())) {
                placements.put(fixture.getUuid(), new StagePlacement(
                        new double[]{fixture.getPositionX(), fixture.getPositionY(), fixture.getPositionZ()},
                        new double[]{fixture.getRotationX(), fixture.getRotationY(), fixture.getRotationZ()}));
            }
        }

        double front = project.getStageDepthCm() / 2 - 70;
        double back = -project.getStageDepthCm() / 2 + 70;
        double top = project.getStageHeightCm() + project.getStageFloorHeightCm();
        double floor = project.getStageFloorHeightCm();

        List<Row> rows = List.of(
                new Row("topFront", top, front, true),
                new Row("bottomFront", floor, front, false),
                new Row("topBack", top, back, true),
                new Row("bottomBack", floor, back, false)
        );

        for (Row row : rows) {
            // the fixtures of the row, each once (the pixels of a fixture follow each other)
            int count = 0;
            String lastFixtureUuid = null;

            for (PresetFixture presetFixture : presetFixtures) {
                Fixture fixture = fixtures.get(presetFixture.getFixtureUuid());

                if (fixture != null && row.positioning().equals(fixture.getPositioning()) && !presetFixture.getFixtureUuid().equals(lastFixtureUuid)) {
                    count++;
                }

                lastFixtureUuid = presetFixture.getFixtureUuid();
            }

            int index = 0;
            lastFixtureUuid = null;

            for (PresetFixture presetFixture : presetFixtures) {
                Fixture fixture = fixtures.get(presetFixture.getFixtureUuid());

                if (fixture != null && row.positioning().equals(fixture.getPositioning())) {
                    if (!presetFixture.getFixtureUuid().equals(lastFixtureUuid)) {
                        index++;
                    }

                    double x = -project.getStageWidthCm() / 2 + (project.getStageWidthCm() / (count + 1)) * index;

                    // a fixture hanging from above hangs below the truss, one on the floor stands on it upside down
                    placements.put(fixture.getUuid(), new StagePlacement(
                            new double[]{x, row.hanging() ? row.y() - 13 : row.y() + 13, row.z()},
                            new double[]{row.hanging() ? 0 : 180, 0, 0}));
                }

                lastFixtureUuid = presetFixture.getFixtureUuid();
            }
        }

        return placements;
    }

    // Where a moving head hanging at the passed place points at the passed spot (in cm), in degrees from the middle of
    // its ranges: in the middle of both, it points straight away from where it is fastened (down, if it hangs). Of the
    // two ways of pointing there, the one turning less on the pan is taken.
    static Aim getAimDegrees(StagePlacement placement, double[] target) {
        double[][] matrix = getRotationMatrix(placement.rotation());
        double[] origin = new double[3];
        double[] world = new double[3];
        double[] local = new double[3];

        // where the head turns, and the spot as seen from it, in the directions of the fixture
        for (int row = 0; row < 3; row++) {
            origin[row] = placement.position()[row] - matrix[row][1] * HEAD_OFFSET_CM;
            world[row] = target[row] - origin[row];
        }

        for (int column = 0; column < 3; column++) {
            local[column] = matrix[0][column] * world[0] + matrix[1][column] * world[1] + matrix[2][column] * world[2];
        }

        double length = Math.sqrt(local[0] * local[0] + local[1] * local[1] + local[2] * local[2]);

        if (length == 0) {
            return new Aim(0, 0);
        }

        double dx = local[0] / length;
        double dy = local[1] / length;
        double dz = local[2] / length;

        // the head points at (-sin(tilt) sin(pan), -cos(tilt), -sin(tilt) cos(pan)) (see ArcTangent)
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double tilt = ArcTangent.atan2(horizontal, -dy);
        double pan = horizontal < 1e-9 ? 0 : ArcTangent.atan2(-dx, -dz);
        double otherPan = pan > 0 ? pan - Math.PI : pan + Math.PI;

        if (Math.abs(otherPan) < Math.abs(pan)) {
            return new Aim((otherPan * 180) / Math.PI, (-tilt * 180) / Math.PI);
        }

        return new Aim((pan * 180) / Math.PI, (tilt * 180) / Math.PI);
    }
}
