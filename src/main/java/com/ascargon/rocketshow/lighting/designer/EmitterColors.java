package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

/**
 * The colors and the color temperature of a preset on the emitters of a fixture besides red, green and blue:
 * <ul>
 * <li>A preset lighting the white LEDs with the white part of its colors (Preset.whiteFromColor) gives a fixture with
 * red, green, blue and white the least of red, green and blue on its white, and only the rest on red, green and blue
 * (pure white on the white alone), unless the preset sets the white itself.</li>
 * <li>The color temperature of a preset plays on a fixture without a channel for it on its warm and its cold white (a
 * bicolor fixture): only the warm one at the warm end, both at full in the middle, only the cold one at the cold end,
 * unless the preset sets either of them itself.</li>
 * </ul>
 * The designer plays them the very same way (emitter-color.ts).
 */
final class EmitterColors {

    private EmitterColors() {
    }

    private static boolean hasCapability(List<CachedFixtureChannel> channels, FixtureCapability.FixtureCapabilityType type, FixtureCapability.FixtureCapabilityColor color) {
        for (CachedFixtureChannel channel : channels) {
            if (channel.getChannel() == null) {
                continue;
            }

            for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
                if (channelCapability.getCapability().getType() == type && (color == null || channelCapability.getCapability().getColor() == color)) {
                    return true;
                }
            }
        }

        return false;
    }

    // the last value of the passed type (and color) saying what to put on the fixture, which wins over the ones before
    private static FixtureCapabilityValue findValue(List<FixtureCapabilityValue> values, FixtureCapability.FixtureCapabilityType type, FixtureCapability.FixtureCapabilityColor color) {
        FixtureCapabilityValue found = null;

        for (FixtureCapabilityValue value : values) {
            if (value.getType() == type && value.getColor() == color && value.getValuePercentage() != null && value.getValuePercentage() >= 0) {
                found = value;
            }
        }

        return found;
    }

    private static FixtureCapabilityValue emitterValue(FixtureCapability.FixtureCapabilityColor color, double valuePercentage) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setType(FixtureCapability.FixtureCapabilityType.ColorIntensity);
        value.setColor(color);
        value.setValuePercentage(valuePercentage);
        return value;
    }

    /**
     * The capability values of a preset as the passed channels of a fixture play them on their emitters (see above). The
     * passed values are returned as they are if nothing changes.
     */
    static List<FixtureCapabilityValue> getEmitterValues(List<FixtureCapabilityValue> values, List<CachedFixtureChannel> channels, boolean whiteFromColor) {
        List<FixtureCapabilityValue> result = values;

        // the color temperature on a warm and a cold white
        FixtureCapabilityValue temperature = findValue(values, FixtureCapability.FixtureCapabilityType.ColorTemperature, null);

        if (temperature != null
                && !hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorTemperature, null)
                && hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.WarmWhite)
                && hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.ColdWhite)) {
            double cold = Math.min(Math.max(temperature.getValuePercentage(), 0), 1);
            List<FixtureCapabilityValue> added = new ArrayList<>();

            if (findValue(values, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.WarmWhite) == null) {
                added.add(emitterValue(FixtureCapability.FixtureCapabilityColor.WarmWhite, Math.min(1, 2 * (1 - cold))));
            }

            if (findValue(values, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.ColdWhite) == null) {
                added.add(emitterValue(FixtureCapability.FixtureCapabilityColor.ColdWhite, Math.min(1, 2 * cold)));
            }

            if (!added.isEmpty()) {
                List<FixtureCapabilityValue> withAdded = new ArrayList<>(result);
                withAdded.addAll(added);
                result = withAdded;
            }
        }

        // the white part of the colors on the white LEDs
        if (!whiteFromColor || !hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.White)) {
            return result;
        }

        List<FixtureCapability.FixtureCapabilityColor> rgb = List.of(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue);

        for (FixtureCapability.FixtureCapabilityColor color : rgb) {
            if (!hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, color)) {
                return result;
            }
        }

        if (findValue(values, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.White) != null) {
            return result;
        }

        List<FixtureCapabilityValue> colors = new ArrayList<>();

        for (FixtureCapability.FixtureCapabilityColor color : rgb) {
            FixtureCapabilityValue value = findValue(values, FixtureCapability.FixtureCapabilityType.ColorIntensity, color);

            if (value == null) {
                return result;
            }

            colors.add(value);
        }

        double white = Math.min(Math.min(colors.get(0).getValuePercentage(), colors.get(1).getValuePercentage()), colors.get(2).getValuePercentage());
        List<FixtureCapabilityValue> withWhite = new ArrayList<>();

        for (FixtureCapabilityValue value : result) {
            int index = indexOfSame(colors, value);

            if (index < 0) {
                withWhite.add(value);
                continue;
            }

            FixtureCapabilityValue rest = value.copy();
            rest.setValuePercentage(value.getValuePercentage() - white);
            withWhite.add(rest);
        }

        withWhite.add(emitterValue(FixtureCapability.FixtureCapabilityColor.White, white));

        return withWhite;
    }

    // where the very same value is in the list (not one equal to it), or -1
    private static int indexOfSame(List<FixtureCapabilityValue> values, FixtureCapabilityValue value) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == value) {
                return i;
            }
        }

        return -1;
    }
}
