package com.ascargon.rocketshow.lighting.designer;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * The capabilities a preset sets as a position between the two ends of what a fixture does with them, whichever
 * fixture it is and whichever way round its profile runs them: from narrow to wide (zoom, also on a channel of the beam
 * angle), near to far (focus), closed to open (iris), no frost to full frost, warm to cold (color temperature), slow to
 * fast (strobe speed, pan/tilt speed, given as a speed or as the time a movement takes), short to long (strobe
 * duration). The strobe opens the shutter at 0 and strobes from slow to fast above it, the prism is out at 0 and in
 * above it, the fog (or haze) is off at 0 and puts out more above it. A preset written this way plays on another model
 * of fixture just the same, which a value of a channel doesn't. The designer plays them the very same way
 * (capability-position.ts).
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
            FixtureCapability.FixtureCapabilityType.FogOutput
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
            entry("strong", 1e9)
    );

    // a number with the unit the fixture library writes after it, e.g. "12.5deg", "3200K" or "120bpm"
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)(deg|%|m\\^3/min|m|K|Hz|bpm|rpm|s|ms|lm)?");

    private CapabilityPositions() {
    }

    static boolean isPositionedType(FixtureCapability.FixtureCapabilityType type) {
        return POSITIONED_TYPES.contains(type);
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
    private static CachedFixtureCapability getWidest(CachedFixtureChannel channel, List<CachedFixtureCapability> capabilities) {
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
    // it. A channel has one capability doing it: the widest one, unless the value says which kind it is.
    static CachedFixtureCapability getPositionedCapability(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type, double valuePercentage) {
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

    // the value of a channel playing a positioned capability value (0 to 1), or null if the channel doesn't do it
    static Double getPositionedChannelValue(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type, double valuePercentage) {
        CachedFixtureCapability channelCapability = getPositionedCapability(channel, type, valuePercentage);

        if (channelCapability == null) {
            return null;
        }

        FixtureCapability capability = channelCapability.getCapability();

        if (isSingleValue(type, valuePercentage, capability)) {
            return capability.getDmxRange() != null && capability.getDmxRange().size() == 2 ? channelCapability.getCenterValue() : null;
        }

        double position = isRangeReversed(capability) ? 1 - valuePercentage : valuePercentage;

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
}
