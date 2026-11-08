package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The programs a fixture runs on its own (its built-in effects and macros, the capabilities "Effect" of its profile),
 * picked by their number on every model: program 1 is the first one of a channel, in the order of its values, program
 * 0 none. A program to the sound is picked among the ones controlled by the sound, if the channel has any. It runs at
 * the speed of the value, if its range runs through speeds (and the speed of its own channel, see EffectSpeed). The
 * designer plays them the very same way (program.ts).
 */
final class Programs {

    private Programs() {
    }

    private static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    // the programs of a channel, to the sound or not, in the order of their values
    static List<CachedFixtureCapability> getPrograms(CachedFixtureChannel channel, boolean sound) {
        List<CachedFixtureCapability> programs = channel.getCapabilities().stream()
                .filter(channelCapability -> channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.Effect)
                .toList();
        List<CachedFixtureCapability> kind = programs.stream()
                .filter(channelCapability -> Boolean.TRUE.equals(channelCapability.getCapability().getSoundControlled()) == sound)
                .toList();
        List<CachedFixtureCapability> ordered = new ArrayList<>(kind.isEmpty() ? programs : kind);

        ordered.sort(Comparator.comparingDouble(Programs::getStart));

        return ordered;
    }

    // where the range of a program starts, 0 for one without a range
    private static double getStart(CachedFixtureCapability program) {
        List<Integer> dmxRange = program.getCapability().getDmxRange();

        return dmxRange != null && !dmxRange.isEmpty() && dmxRange.get(0) != null ? dmxRange.get(0) : 0;
    }

    /**
     * The value of a channel running the passed program (1 for the first one) at the passed speed (0 to 1), none (0)
     * as "no function" of the channel, or null if the channel doesn't have it.
     */
    static Double getProgramChannelValue(CachedFixtureChannel channel, Double programNumber, Boolean sound, Double speed) {
        long number = Math.round(programNumber == null || programNumber.isNaN() ? 0 : programNumber);

        if (number <= 0) {
            CachedFixtureCapability off = CapabilityPositions.getOff(channel, FixtureCapability.FixtureCapabilityType.Effect);
            return off == null ? null : CapabilityPositions.getCenter(off);
        }

        List<CachedFixtureCapability> programs = getPrograms(channel, Boolean.TRUE.equals(sound));

        if (number > programs.size()) {
            return null;
        }

        CachedFixtureCapability program = programs.get((int) number - 1);
        FixtureCapability capability = program.getCapability();

        if ((isEmpty(capability.getSpeedStart()) && isEmpty(capability.getSpeedEnd())) || speed == null || !(speed >= 0)) {
            return CapabilityPositions.getCenter(program);
        }

        return CapabilityPositions.getRangeValue(channel, program, CapabilityPositions.isRangeReversed(capability) ? 1 - speed : speed);
    }
}
