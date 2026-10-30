package com.ascargon.rocketshow.lighting.designer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * The capabilities a preset sets as a position between the two ends of what a fixture does with them, whichever
 * fixture it is and whichever way round its profile runs them: from narrow to wide (zoom, also on a channel of the beam
 * angle), near to far (focus), closed to open (iris), no frost to full frost, warm to cold (color temperature), slow to
 * fast (strobe speed, pan/tilt speed, given as a speed or as the time a movement takes), short to long (strobe
 * duration), out to in (the blades of a framing shutter), from one end of their turn to the other (the blades and the
 * whole framing shutter turned). The strobe opens the shutter at 0 and strobes from slow to fast above it, the prism is
 * out at 0 and in above it, the fog (or haze) is off at 0 and puts out more above it, the pulse of an iris or a frost
 * is off at 0 and pulses from slow to fast above it. A rotation (of a gobo, a prism or of anything else turning) turns
 * anticlockwise below the middle (fastest at 0), stops in the middle and turns clockwise above it (fastest at 1). A
 * preset written this way plays on another model of fixture just the same, which a value of a channel doesn't. The
 * designer plays them the very same way (capability-position.ts).
 */
final class CapabilityPositions {

    private static final List<FixtureCapability.FixtureCapabilityType> POSITIONED_TYPES = List.of(
            FixtureCapability.FixtureCapabilityType.ShutterStrobe,
            FixtureCapability.FixtureCapabilityType.StrobeSpeed,
            FixtureCapability.FixtureCapabilityType.StrobeDuration,
            FixtureCapability.FixtureCapabilityType.Zoom,
            FixtureCapability.FixtureCapabilityType.Focus,
            FixtureCapability.FixtureCapabilityType.Iris,
            FixtureCapability.FixtureCapabilityType.Frost,
            FixtureCapability.FixtureCapabilityType.ColorTemperature,
            FixtureCapability.FixtureCapabilityType.Prism,
            FixtureCapability.FixtureCapabilityType.PanTiltSpeed,
            FixtureCapability.FixtureCapabilityType.FogOutput,
            FixtureCapability.FixtureCapabilityType.WheelSlotRotation,
            FixtureCapability.FixtureCapabilityType.PrismRotation,
            FixtureCapability.FixtureCapabilityType.Rotation,
            FixtureCapability.FixtureCapabilityType.BladeInsertion,
            FixtureCapability.FixtureCapabilityType.BladeRotation,
            FixtureCapability.FixtureCapabilityType.BladeSystemRotation,
            FixtureCapability.FixtureCapabilityType.IrisEffect,
            FixtureCapability.FixtureCapabilityType.FrostEffect
    );

    // the rotations, which turn either way
    private static final List<FixtureCapability.FixtureCapabilityType> ROTATION_TYPES = List.of(
            FixtureCapability.FixtureCapabilityType.WheelSlotRotation,
            FixtureCapability.FixtureCapabilityType.PrismRotation,
            FixtureCapability.FixtureCapabilityType.Rotation
    );

    // What plays over what the rest of a preset puts on the same channel, whatever order they come in: the pulse of an
    // iris over the iris, the one of a frost over the frost and the shake of a gobo over the gobo. Each of them leaves
    // the channel to the rest at 0.
    private static final List<FixtureCapability.FixtureCapabilityType> OVERRIDING_TYPES = List.of(
            FixtureCapability.FixtureCapabilityType.IrisEffect,
            FixtureCapability.FixtureCapabilityType.FrostEffect,
            FixtureCapability.FixtureCapabilityType.WheelShake
    );

    // where the speeds of a rotation lie: clockwise above 0, anticlockwise below it
    private static final Map<String, Double> ROTATION_WORD_RANKS = Map.of(
            "fast CW", 1e9,
            "slow CW", 1e-9,
            "stop", 0d,
            "slow CCW", -1e-9,
            "fast CCW", -1e9
    );

    // where the words of the fixture library lie among the numbers it gives the same property in (e.g. "narrow" below
    // any angle and "wide" above it), to tell which way round a range runs
    private static final Map<String, Double> WORD_RANKS = Map.ofEntries(
            entry("closed", -2e9),
            entry("narrow", -1e9),
            entry("wide", 1e9),
            entry("open", 1e9),
            entry("near", -1e9),
            entry("far", 1e9),
            entry("off", -2e9),
            entry("low", -1e9),
            entry("high", 1e9),
            entry("warm", -2e9),
            entry("CTO", -1e9),
            entry("default", 6500d),
            entry("CTB", 1e9),
            entry("cold", 2e9),
            entry("stop", -2e9),
            entry("slow", -1e9),
            entry("fast", 1e9),
            entry("instant", -2e9),
            entry("short", -1e9),
            entry("long", 1e9),
            entry("weak", -1e9),
            entry("strong", 1e9),
            entry("out", -1e9),
            entry("in", 1e9)
    );

    // a number with the unit the fixture library writes after it, e.g. "12.5deg", "3200K" or "120bpm"
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)(deg|%|m\\^3/min|m|K|Hz|bpm|rpm|s|ms|lm)?");

    private CapabilityPositions() {
    }

    static boolean isPositionedType(FixtureCapability.FixtureCapabilityType type) {
        return POSITIONED_TYPES.contains(type);
    }

    static boolean isRotationType(FixtureCapability.FixtureCapabilityType type) {
        return ROTATION_TYPES.contains(type);
    }

    static boolean isOverridingType(FixtureCapability.FixtureCapabilityType type) {
        return OVERRIDING_TYPES.contains(type);
    }

    // do the capabilities of the type turn a blade of a framing shutter, which the value names?
    static boolean isBladeType(FixtureCapability.FixtureCapabilityType type) {
        return type == FixtureCapability.FixtureCapabilityType.BladeInsertion || type == FixtureCapability.FixtureCapabilityType.BladeRotation;
    }

    // the capabilities of a channel a positioned capability is played with: the zoom also on a channel of the beam
    // angle, the fog output also on a channel of the fog (which names its kind as well, e.g. haze)
    static List<FixtureCapability.FixtureCapabilityType> getPlayingTypes(FixtureCapability.FixtureCapabilityType type) {
        if (type == FixtureCapability.FixtureCapabilityType.Zoom) {
            return List.of(FixtureCapability.FixtureCapabilityType.Zoom, FixtureCapability.FixtureCapabilityType.BeamAngle);
        }

        if (type == FixtureCapability.FixtureCapabilityType.FogOutput) {
            return List.of(FixtureCapability.FixtureCapabilityType.FogOutput, FixtureCapability.FixtureCapabilityType.Fog);
        }

        return List.of(type);
    }

    // where a property of the fixture library lies (e.g. "10deg", "wide", "120bpm", which is 2 Hz, or "500ms", which is
    // half a second), or null for one which can't be read
    static Double getPropertyRank(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();

        if (WORD_RANKS.containsKey(trimmed)) {
            return WORD_RANKS.get(trimmed);
        }

        Matcher matcher = NUMBER_WITH_UNIT.matcher(trimmed);

        if (!matcher.matches()) {
            return null;
        }

        double value = Double.parseDouble(matcher.group(1));

        if (matcher.group(2) == null) {
            return value;
        }

        return switch (matcher.group(2)) {
            case "bpm", "rpm" -> value / 60;
            case "ms" -> value / 1000;
            default -> value;
        };
    }

    // is a property of the fixture library nothing at all, e.g. no fog ("off" or 0)?
    private static boolean isNothing(String text) {
        if (text == null) {
            return false;
        }

        Double rank = getPropertyRank(text);

        return "off".equals(text.trim()) || (rank != null && rank == 0);
    }

    // The property telling where the range of a capability starts and where it ends, and whether it runs the other way
    // round than the capability does: a movement given by the time it takes is the slower the longer it takes.
    private record RangeEnds(String start, String end, boolean inverted) {
    }

    // the ends of a property, given for both ends of a range or for all of it at once
    private static RangeEnds ends(String start, String end, String all) {
        return new RangeEnds(isEmpty(start) ? all : start, isEmpty(end) ? all : end, false);
    }

    private static RangeEnds getRangeEnds(FixtureCapability capability) {
        if (capability.getType() == null) {
            return new RangeEnds(null, null, false);
        }

        return switch (capability.getType()) {
            case Zoom, BeamAngle -> new RangeEnds(capability.getAngleStart(), capability.getAngleEnd(), false);
            case Focus -> new RangeEnds(capability.getDistanceStart(), capability.getDistanceEnd(), false);
            case Iris -> new RangeEnds(capability.getOpenPercentStart(), capability.getOpenPercentEnd(), false);
            case Frost -> new RangeEnds(capability.getFrostIntensityStart(), capability.getFrostIntensityEnd(), false);
            case ColorTemperature -> new RangeEnds(capability.getColorTemperatureStart(), capability.getColorTemperatureEnd(), false);
            case ShutterStrobe, StrobeSpeed -> new RangeEnds(capability.getSpeedStart(), capability.getSpeedEnd(), false);
            case StrobeDuration -> new RangeEnds(capability.getDurationStart(), capability.getDurationEnd(), false);
            case PanTiltSpeed -> !isEmpty(capability.getSpeedStart()) || !isEmpty(capability.getSpeedEnd())
                    ? new RangeEnds(capability.getSpeedStart(), capability.getSpeedEnd(), false)
                    : new RangeEnds(capability.getDurationStart(), capability.getDurationEnd(), true);
            case FogOutput, Fog -> ends(capability.getFogOutputStart(), capability.getFogOutputEnd(), capability.getFogOutput());
            case BladeInsertion -> ends(capability.getInsertionStart(), capability.getInsertionEnd(), capability.getInsertion());
            case BladeRotation, BladeSystemRotation -> ends(capability.getAngleStart(), capability.getAngleEnd(), capability.getAngle());
            case IrisEffect, FrostEffect -> ends(capability.getSpeedStart(), capability.getSpeedEnd(), capability.getSpeed());
            case WheelShake -> ends(capability.getShakeSpeedStart(), capability.getShakeSpeedEnd(), capability.getShakeSpeed());
            default -> new RangeEnds(null, null, false);
        };
    }

    // does the range of the capability run from its high end to its low end (e.g. from wide to narrow)? A range which
    // doesn't say runs from low to high.
    static boolean isRangeReversed(FixtureCapability capability) {
        RangeEnds range = getRangeEnds(capability);
        Double start = getPropertyRank(range.start());
        Double end = getPropertyRank(range.end());

        return start != null && end != null && (range.inverted() ? start < end : start > end);
    }

    // does a capability put out nothing over all of its range, e.g. a fog machine switched off?
    private static boolean putsOutNothing(FixtureCapability capability) {
        RangeEnds range = getRangeEnds(capability);

        return isNothing(range.start()) && isNothing(range.end());
    }

    private static boolean hasDmxRange(FixtureCapability capability) {
        return capability.getDmxRange() != null && capability.getDmxRange().size() >= 2;
    }

    // how much of the channel a capability takes: one without a range of its own takes all of it
    private static double getRangeWidth(CachedFixtureChannel channel, CachedFixtureCapability channelCapability) {
        FixtureCapability capability = channelCapability.getCapability();

        if (!hasDmxRange(capability)) {
            return channel.getMaxValue();
        }

        return capability.getDmxRange().get(1) - capability.getDmxRange().get(0);
    }

    // the widest of the passed capabilities of the channel, the first of them as wide
    static CachedFixtureCapability getWidest(CachedFixtureChannel channel, List<CachedFixtureCapability> capabilities) {
        CachedFixtureCapability widest = null;

        for (CachedFixtureCapability channelCapability : capabilities) {
            if (widest == null || getRangeWidth(channel, channelCapability) > getRangeWidth(channel, widest)) {
                widest = channelCapability;
            }
        }

        return widest;
    }

    // the capabilities of the channel a positioned capability is played with (see getPlayingTypes)
    private static List<CachedFixtureCapability> ofType(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        List<FixtureCapability.FixtureCapabilityType> types = getPlayingTypes(type);

        return channel.getCapabilities().stream().filter(channelCapability -> types.contains(channelCapability.getCapability().getType())).toList();
    }

    // the capability of the channel switching the passed one off: "no function" on a channel which has the passed one
    // (e.g. "no strobe" below the strobe of a par)
    private static CachedFixtureCapability getOff(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        if (ofType(channel, type).isEmpty()) {
            return null;
        }

        for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
            if (channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.NoFunction) {
                return channelCapability;
            }
        }

        return null;
    }

    // the capability of the channel a positioned capability value is played with, or null if the channel doesn't do
    // it. A channel has one capability doing it: the widest one, unless the value says which kind it is. A blade is
    // played by the capabilities of the blade the value names.
    static CachedFixtureCapability getPositionedCapability(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type, double valuePercentage, String blade) {
        if (isBladeType(type)) {
            return getWidest(channel, ofType(channel, type).stream()
                    .filter(channelCapability -> isEmpty(blade) || blade.equals(channelCapability.getCapability().getBlade()))
                    .toList());
        }

        if ((type == FixtureCapability.FixtureCapabilityType.IrisEffect || type == FixtureCapability.FixtureCapabilityType.FrostEffect) && valuePercentage <= 0) {
            // no pulse leaves the iris or the frost to the rest of the preset
            return null;
        }

        if (type == FixtureCapability.FixtureCapabilityType.ShutterStrobe) {
            if (valuePercentage > 0) {
                // a strobe flashing on its own, not at random or to the sound, or else any of them
                List<CachedFixtureCapability> strobes = ofType(channel, type).stream()
                        .filter(channelCapability -> "Strobe".equals(channelCapability.getCapability().getShutterEffect()))
                        .toList();
                List<CachedFixtureCapability> regular = strobes.stream()
                        .filter(channelCapability -> !Boolean.TRUE.equals(channelCapability.getCapability().getRandomTiming()) && !Boolean.TRUE.equals(channelCapability.getCapability().getSoundControlled()))
                        .toList();

                return getWidest(channel, regular.isEmpty() ? strobes : regular);
            }

            // the shutter open, or else no strobe
            for (CachedFixtureCapability channelCapability : ofType(channel, type)) {
                if ("Open".equals(channelCapability.getCapability().getShutterEffect())) {
                    return channelCapability;
                }
            }

            return getOff(channel, type);
        }

        if (type == FixtureCapability.FixtureCapabilityType.Prism) {
            if (valuePercentage > 0) {
                // a prism standing still, or else any of them
                List<CachedFixtureCapability> prisms = ofType(channel, type);

                for (CachedFixtureCapability channelCapability : prisms) {
                    if (isEmpty(channelCapability.getCapability().getSpeedStart()) && isEmpty(channelCapability.getCapability().getSpeed())) {
                        return channelCapability;
                    }
                }

                return prisms.isEmpty() ? null : prisms.getFirst();
            }

            return getOff(channel, type);
        }

        if (type == FixtureCapability.FixtureCapabilityType.FogOutput && valuePercentage <= 0) {
            // the fog off, or else no function, or else the low end of what it puts out
            for (CachedFixtureCapability channelCapability : ofType(channel, type)) {
                if (putsOutNothing(channelCapability.getCapability())) {
                    return channelCapability;
                }
            }

            CachedFixtureCapability off = getOff(channel, type);

            return off != null ? off : getWidest(channel, ofType(channel, type));
        }

        return getWidest(channel, ofType(channel, type));
    }

    // Is a capability played at a single value (its middle) instead of at a position within it: what switches the
    // capability off (the shutter open, no strobe, no fog) and a prism in or out
    private static boolean isSingleValue(FixtureCapability.FixtureCapabilityType type, double valuePercentage, FixtureCapability capability) {
        if (!getPlayingTypes(type).contains(capability.getType()) || type == FixtureCapability.FixtureCapabilityType.Prism) {
            return true;
        }

        if (type == FixtureCapability.FixtureCapabilityType.ShutterStrobe && valuePercentage <= 0) {
            return true;
        }

        return type == FixtureCapability.FixtureCapabilityType.FogOutput && valuePercentage <= 0 && putsOutNothing(capability);
    }

    // an empty text is none, like in the designer
    private static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    // the middle of a capability, or null for one without a range of its own (like in the designer)
    private static Double getCenter(CachedFixtureCapability channelCapability) {
        FixtureCapability capability = channelCapability.getCapability();

        return capability.getDmxRange() != null && capability.getDmxRange().size() == 2 ? channelCapability.getCenterValue() : null;
    }

    // the value of a channel playing a positioned capability value (0 to 1), or null if the channel doesn't do it
    static Double getPositionedChannelValue(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type, double valuePercentage, String blade) {
        if (isRotationType(type)) {
            return getRotationChannelValue(channel, type, valuePercentage);
        }

        CachedFixtureCapability channelCapability = getPositionedCapability(channel, type, valuePercentage, blade);

        if (channelCapability == null) {
            return null;
        }

        FixtureCapability capability = channelCapability.getCapability();

        if (isSingleValue(type, valuePercentage, capability)) {
            return getCenter(channelCapability);
        }

        double position = isRangeReversed(capability) ? 1 - valuePercentage : valuePercentage;

        return getRangeValue(channel, channelCapability, position);
    }

    // the value at the passed position (0 to 1) within the range of a capability of the channel
    private static Double getRangeValue(CachedFixtureChannel channel, CachedFixtureCapability channelCapability, double position) {
        FixtureCapability capability = channelCapability.getCapability();

        if (channel.getCapabilities().size() == 1) {
            // the whole channel
            return channel.getMaxValue() * position;
        }

        if (!hasDmxRange(capability)) {
            return null;
        }

        List<Integer> dmxRange = capability.getDmxRange();

        return dmxRange.get(0) + (dmxRange.get(1) - dmxRange.get(0)) * position;
    }

    // where a speed of a rotation lies (e.g. "fast CW", "stop", "-10rpm" or "50%"), clockwise above 0, or null for one
    // which can't be read
    static Double getRotationRank(String text) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();

        if (ROTATION_WORD_RANKS.containsKey(trimmed)) {
            return ROTATION_WORD_RANKS.get(trimmed);
        }

        return getPropertyRank(trimmed);
    }

    // the speeds a rotation runs between, or null for one turning to an angle instead (e.g. an indexed gobo)
    private static double[] getRotationEnds(CachedFixtureCapability channelCapability) {
        FixtureCapability capability = channelCapability.getCapability();
        Double start = getRotationRank(isEmpty(capability.getSpeedStart()) ? capability.getSpeed() : capability.getSpeedStart());
        Double end = getRotationRank(isEmpty(capability.getSpeedEnd()) ? capability.getSpeed() : capability.getSpeedEnd());

        return start == null || end == null ? null : new double[]{start, end};
    }

    // the value of a range turning both ways, from where it stops towards the passed direction
    private static Double getAcrossValue(CachedFixtureChannel channel, List<CachedFixtureCapability> across, double direction, double amount) {
        CachedFixtureCapability widest = getWidest(channel, across);
        double[] ends = getRotationEnds(widest);
        double stop = ends[0] / (ends[0] - ends[1]);
        boolean towardsEnd = Math.signum(ends[1]) == direction;

        return getRangeValue(channel, widest, towardsEnd ? stop + (1 - stop) * amount : stop - stop * amount);
    }

    // The value of a channel turning something at the passed speed: anticlockwise below the middle (fastest at 0),
    // stopped in the middle, clockwise above it (fastest at 1), or null if the channel doesn't turn it that way. The
    // widest range turning the asked way only is taken from slow to fast, or else one turning both ways, from where it
    // stops.
    static Double getRotationChannelValue(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type, double valuePercentage) {
        double speed = Math.max(Math.min(valuePercentage * 2 - 1, 1), -1);
        List<CachedFixtureCapability> rotations = ofType(channel, type).stream()
                .filter(channelCapability -> getRotationEnds(channelCapability) != null)
                .toList();

        if (rotations.isEmpty()) {
            return null;
        }

        // the ranges turning both ways
        List<CachedFixtureCapability> across = rotations.stream()
                .filter(channelCapability -> {
                    double[] ends = getRotationEnds(channelCapability);
                    return ends[0] * ends[1] < 0;
                })
                .toList();

        if (speed == 0) {
            for (CachedFixtureCapability channelCapability : rotations) {
                double[] ends = getRotationEnds(channelCapability);

                if (ends[0] == 0 && ends[1] == 0) {
                    return getCenter(channelCapability);
                }
            }

            if (!across.isEmpty()) {
                return getAcrossValue(channel, across, 1, 0);
            }

            CachedFixtureCapability off = getOff(channel, type);

            return off == null ? null : getCenter(off);
        }

        double direction = Math.signum(speed);
        List<CachedFixtureCapability> oneWay = rotations.stream()
                .filter(channelCapability -> {
                    double[] ends = getRotationEnds(channelCapability);
                    return ends[0] * direction >= 0 && ends[1] * direction >= 0 && (ends[0] != 0 || ends[1] != 0);
                })
                .toList();

        if (!oneWay.isEmpty()) {
            CachedFixtureCapability widest = getWidest(channel, oneWay);
            double[] ends = getRotationEnds(widest);

            return getRangeValue(channel, widest, Math.abs(ends[0]) > Math.abs(ends[1]) ? 1 - Math.abs(speed) : Math.abs(speed));
        }

        return across.isEmpty() ? null : getAcrossValue(channel, across, direction, Math.abs(speed));
    }

    // the wheel a capability shakes: the one of its slot, the one it names or else the one named like its channel
    private static String getShakeWheelName(CachedFixtureChannel channel, CachedFixtureCapability channelCapability) {
        if (!isEmpty(channelCapability.getWheelName())) {
            return channelCapability.getWheelName();
        }

        return channelCapability.getCapability().getWheel() instanceof String name ? name : channel.getName();
    }

    // The value of a channel shaking the passed slot of a wheel (or the whole wheel) at the passed speed, from slow to
    // fast, or null if the channel doesn't shake it or the speed is 0 (which leaves the wheel to the rest)
    static Double getShakeChannelValue(CachedFixtureChannel channel, String wheel, Double slotNumber, double valuePercentage) {
        if (!(valuePercentage > 0)) {
            return null;
        }

        List<CachedFixtureCapability> shakes = channel.getCapabilities().stream()
                .filter(channelCapability -> channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.WheelShake
                        && (isEmpty(wheel) || wheel.equals(getShakeWheelName(channel, channelCapability)))
                        && (channelCapability.getCapability().getSlotNumber() == null || Objects.equals(channelCapability.getCapability().getSlotNumber(), slotNumber)))
                .toList();

        // the slot shaking on its own, or else the whole wheel
        List<CachedFixtureCapability> ofSlot = shakes.stream()
                .filter(channelCapability -> Objects.equals(channelCapability.getCapability().getSlotNumber(), slotNumber))
                .toList();
        CachedFixtureCapability shake = getWidest(channel, ofSlot.isEmpty() ? shakes : ofSlot);

        if (shake == null) {
            return null;
        }

        return getRangeValue(channel, shake, isRangeReversed(shake.getCapability()) ? 1 - valuePercentage : valuePercentage);
    }
}
