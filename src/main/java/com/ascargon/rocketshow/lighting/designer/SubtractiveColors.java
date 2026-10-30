package com.ascargon.rocketshow.lighting.designer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A fixture mixing its colors with filters (cyan, magenta and yellow, e.g. a spot) instead of lighting red, green and
 * blue is given the red, green and blue of a preset as the filters taking away what it doesn't light (full red is no
 * cyan, full magenta and full yellow), so a preset lights it in its colors as well. The designer plays them the very
 * same way (subtractive-color.ts).
 */
final class SubtractiveColors {

    // the filter taking away each of the colors a fixture lights with
    private static final Map<FixtureCapability.FixtureCapabilityColor, FixtureCapability.FixtureCapabilityColor> FILTERS = Map.of(
            FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Cyan,
            FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Magenta,
            FixtureCapability.FixtureCapabilityColor.Blue, FixtureCapability.FixtureCapabilityColor.Yellow
    );

    private SubtractiveColors() {
    }

    private static boolean hasColor(List<CachedFixtureChannel> channels, Collection<FixtureCapability.FixtureCapabilityColor> colors) {
        for (CachedFixtureChannel channel : channels) {
            if (channel.getChannel() == null) {
                continue;
            }

            for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
                if (channelCapability.getCapability().getType() == FixtureCapability.FixtureCapabilityType.ColorIntensity && colors.contains(channelCapability.getCapability().getColor())) {
                    return true;
                }
            }
        }

        return false;
    }

    // Does a fixture mix its colors with filters, instead of lighting red, green and blue?
    static boolean mixesSubtractively(List<CachedFixtureChannel> channels) {
        return !hasColor(channels, FILTERS.keySet()) && hasColor(channels, FILTERS.values());
    }

    // the capability values the passed channels of a fixture play
    static List<FixtureCapabilityValue> getFixtureCapabilityValues(List<FixtureCapabilityValue> capabilityValues, List<CachedFixtureChannel> channels) {
        if (!mixesSubtractively(channels)) {
            return capabilityValues;
        }

        return capabilityValues.stream().map(capabilityValue -> {
            FixtureCapability.FixtureCapabilityColor filter = capabilityValue.getColor() == null ? null : FILTERS.get(capabilityValue.getColor());

            if (capabilityValue.getType() != FixtureCapability.FixtureCapabilityType.ColorIntensity || filter == null
                    || capabilityValue.getValuePercentage() == null || capabilityValue.getValuePercentage() < 0) {
                return capabilityValue;
            }

            FixtureCapabilityValue filterValue = new FixtureCapabilityValue();
            filterValue.setType(capabilityValue.getType());
            filterValue.setProfileUuid(capabilityValue.getProfileUuid());
            filterValue.setWheel(capabilityValue.getWheel());
            filterValue.setSlotNumber(capabilityValue.getSlotNumber());
            filterValue.setColor(filter);
            filterValue.setValuePercentage(1 - capabilityValue.getValuePercentage());

            return filterValue;
        }).toList();
    }
}
