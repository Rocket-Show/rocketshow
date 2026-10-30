package com.ascargon.rocketshow.lighting.designer;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/**
 * The capabilities a preset sets as a position between the two ends of what a fixture does with them, whichever
 * fixture it is and whichever way round its profile runs them: from narrow to wide (zoom), near to far (focus), closed
 * to open (iris), no frost to full frost, warm to cold (color temperature), slow to fast (strobe speed). The strobe
 * opens the shutter at 0 and strobes from slow to fast above it, the prism is out at 0 and in above it. A preset
 * written this way plays on another model of fixture just the same, which a value of a channel doesn't. The designer
 * plays them the very same way (capability-position.ts).
 */
final class CapabilityPositions {

    private static final List<FixtureCapability.FixtureCapabilityType> POSITIONED_TYPES = List.of(
            FixtureCapability.FixtureCapabilityType.ShutterStrobe,
            FixtureCapability.FixtureCapabilityType.StrobeSpeed,
            FixtureCapability.FixtureCapabilityType.Zoom,
            FixtureCapability.FixtureCapabilityType.Focus,
            FixtureCapability.FixtureCapabilityType.Iris,
            FixtureCapability.FixtureCapabilityType.Frost,
            FixtureCapability.FixtureCapabilityType.ColorTemperature,
            FixtureCapability.FixtureCapabilityType.Prism
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
            entry("fast", 1e9)
    );

    // a number with the unit the fixture library writes after it, e.g. "12.5deg", "3200K" or "120bpm"
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)(deg|%|m|K|Hz|bpm|s|ms|lm)?");

    private CapabilityPositions() {
    }

    static boolean isPositionedType(FixtureCapability.FixtureCapabilityType type) {
        return POSITIONED_TYPES.contains(type);
    }

    // where a property of the fixture library lies (e.g. "10deg", "wide" or "120bpm", which is 2 Hz), or null for one
    // which can't be read
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

        return "bpm".equals(matcher.group(2)) ? value / 60 : value;
    }

    // the property telling where the range of a capability starts and where it ends
    private static String[] getRangeEnds(FixtureCapability capability) {
        if (capability.getType() == null) {
            return new String[]{null, null};
        }

        return switch (capability.getType()) {
            case Zoom -> new String[]{capability.getAngleStart(), capability.getAngleEnd()};
            case Focus -> new String[]{capability.getDistanceStart(), capability.getDistanceEnd()};
            case Iris -> new String[]{capability.getOpenPercentStart(), capability.getOpenPercentEnd()};
            case Frost -> new String[]{capability.getFrostIntensityStart(), capability.getFrostIntensityEnd()};
            case ColorTemperature -> new String[]{capability.getColorTemperatureStart(), capability.getColorTemperatureEnd()};
            case ShutterStrobe, StrobeSpeed -> new String[]{capability.getSpeedStart(), capability.getSpeedEnd()};
            default -> new String[]{null, null};
        };
    }

    // does the range of the capability run from its high end to its low end (e.g. from wide to narrow)? A range which
    // doesn't say runs from low to high.
    static boolean isRangeReversed(FixtureCapability capability) {
        String[] ends = getRangeEnds(capability);
        Double start = getPropertyRank(ends[0]);
        Double end = getPropertyRank(ends[1]);

        return start != null && end != null && start > end;
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

    private static List<CachedFixtureCapability> ofType(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        return channel.getCapabilities().stream().filter(channelCapability -> channelCapability.getCapability().getType() == type).toList();
    }

    // the capability of the channel switching the passed one off: "no function" on a channel which has the passed one
    // (e.g. "no strobe" below the strobe of a par)
    private static CachedFixtureCapability getOff(CachedFixtureChannel channel, FixtureCapability.FixtureCapabilityType type) {
        if (ofType(channel, type).isEmpty()) {
            return null;
        }

        List<CachedFixtureCapability> off = ofType(channel, FixtureCapability.FixtureCapabilityType.NoFunction);

        return off.isEmpty() ? null : off.getFirst();
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

        return getWidest(channel, ofType(channel, type));
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

        // the shutter open, a strobe switched off and a prism in or out are a single value
        if (capability.getType() != type || type == FixtureCapability.FixtureCapabilityType.Prism || (type == FixtureCapability.FixtureCapabilityType.ShutterStrobe && valuePercentage <= 0)) {
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
