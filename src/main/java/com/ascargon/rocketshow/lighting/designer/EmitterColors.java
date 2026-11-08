package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

/**
 * The colors and the color temperature of a preset on the emitters of a fixture besides red, green and blue:
 * <ul>
 * <li>The color temperature of a preset plays on a fixture without a channel for it on its warm and its cold white (a
 * bicolor fixture): only the warm one at the warm end, both at full in the middle, only the cold one at the cold end.
 * A fixture with red, green and blue but without a warm and a cold white shows it as a color: warm white, white in the
 * middle, a cold white. Neither, if the preset sets either of them (or any of red, green and blue) itself.</li>
 * <li>A preset lighting the white and the amber LEDs with the parts of its colors (Preset.emittersFromColor) gives a
 * fixture with red, green, blue and white the least of red, green and blue on its white, and of the rest the amber part
 * (all of the red with three quarters as much of the green) on its amber, only the rest on red, green and blue, unless
 * the preset sets the white or the amber itself.</li>
 * </ul>
 * The designer plays them the very same way (emitter-color.ts).
 */
final class EmitterColors {

    // the color a color temperature is shown with on red, green and blue: at the warm end, in the middle, at the cold end
    private static final double[][] TEMPERATURE_COLORS = {
            {1, 0.6, 0.3},
            {1, 1, 1},
            {0.8, 0.88, 1},
    };

    // how much green the light of an amber LED has, the red at full
    private static final double AMBER_GREEN = 0.75;

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
    static List<FixtureCapabilityValue> getEmitterValues(List<FixtureCapabilityValue> values, List<CachedFixtureChannel> channels, boolean emittersFromColor) {
        List<FixtureCapabilityValue> result = values;
        List<FixtureCapability.FixtureCapabilityColor> rgb = List.of(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue);
        boolean hasRgb = true;

        for (FixtureCapability.FixtureCapabilityColor color : rgb) {
            if (!hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, color)) {
                hasRgb = false;
            }
        }

        // the color temperature on a warm and a cold white, or as a color
        FixtureCapabilityValue temperature = findValue(values, FixtureCapability.FixtureCapabilityType.ColorTemperature, null);

        if (temperature != null && !hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorTemperature, null)) {
            double cold = Math.min(Math.max(temperature.getValuePercentage(), 0), 1);

            if (hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.WarmWhite)
                    && hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.ColdWhite)) {
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
            } else if (hasRgb && noneOf(values, rgb)) {
                int half = cold < 0.5 ? 0 : 1;
                double share = cold < 0.5 ? cold * 2 : cold * 2 - 1;
                List<FixtureCapabilityValue> withColor = new ArrayList<>(result);

                for (int index = 0; index < rgb.size(); index++) {
                    withColor.add(emitterValue(rgb.get(index),
                            TEMPERATURE_COLORS[half][index] + (TEMPERATURE_COLORS[half + 1][index] - TEMPERATURE_COLORS[half][index]) * share));
                }

                result = withColor;
            }
        }

        // the white and the amber parts of the colors on their LEDs
        if (!emittersFromColor || !hasRgb) {
            return result;
        }

        boolean white = hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.White)
                && findValue(result, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.White) == null;
        boolean amber = hasCapability(channels, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Amber)
                && findValue(result, FixtureCapability.FixtureCapabilityType.ColorIntensity, FixtureCapability.FixtureCapabilityColor.Amber) == null;
        List<FixtureCapabilityValue> colors = new ArrayList<>();

        for (FixtureCapability.FixtureCapabilityColor color : rgb) {
            colors.add(findValue(result, FixtureCapability.FixtureCapabilityType.ColorIntensity, color));
        }

        if ((!white && !amber) || colors.contains(null)) {
            return result;
        }

        double red = colors.get(0).getValuePercentage();
        double green = colors.get(1).getValuePercentage();
        double blue = colors.get(2).getValuePercentage();
        List<FixtureCapabilityValue> added = new ArrayList<>();

        if (white) {
            double whitePart = Math.min(Math.min(red, green), blue);
            red -= whitePart;
            green -= whitePart;
            blue -= whitePart;
            added.add(emitterValue(FixtureCapability.FixtureCapabilityColor.White, whitePart));
        }

        if (amber) {
            double amberPart = Math.min(red, green / AMBER_GREEN);
            red -= amberPart;
            green -= amberPart * AMBER_GREEN;
            added.add(emitterValue(FixtureCapability.FixtureCapabilityColor.Amber, amberPart));
        }

        double[] rest = {red, green, blue};
        List<FixtureCapabilityValue> withParts = new ArrayList<>();

        for (FixtureCapabilityValue value : result) {
            int index = indexOfSame(colors, value);

            if (index < 0) {
                withParts.add(value);
                continue;
            }

            FixtureCapabilityValue restValue = value.copy();
            restValue.setValuePercentage(rest[index]);
            withParts.add(restValue);
        }

        withParts.addAll(added);

        return withParts;
    }

    // does the step set none of the passed colors?
    private static boolean noneOf(List<FixtureCapabilityValue> values, List<FixtureCapability.FixtureCapabilityColor> colors) {
        for (FixtureCapability.FixtureCapabilityColor color : colors) {
            if (findValue(values, FixtureCapability.FixtureCapabilityType.ColorIntensity, color) != null) {
                return false;
            }
        }

        return true;
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
