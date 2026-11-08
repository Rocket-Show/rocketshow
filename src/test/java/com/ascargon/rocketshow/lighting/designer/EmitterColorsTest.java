package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * White from the colors of a preset on the white LEDs and the color temperature on a warm and a cold white, like
 * emitter-color.spec.ts of the designer (the conformance cases play them on whole fixtures).
 */
class EmitterColorsTest {

    private static final FixtureCapability.FixtureCapabilityType COLOR = FixtureCapability.FixtureCapabilityType.ColorIntensity;

    // the channels of a fixture with one channel for each of the passed capabilities
    private static List<CachedFixtureChannel> fixture(Object... typesAndColors) {
        List<CachedFixtureChannel> channels = new ArrayList<>();

        for (Object typeOrColor : typesAndColors) {
            FixtureCapability capability = new FixtureCapability();

            if (typeOrColor instanceof FixtureCapability.FixtureCapabilityColor color) {
                capability.setType(COLOR);
                capability.setColor(color);
            } else {
                capability.setType((FixtureCapability.FixtureCapabilityType) typeOrColor);
            }

            CachedFixtureCapability cachedCapability = new CachedFixtureCapability();
            cachedCapability.setCapability(capability);
            CachedFixtureChannel channel = new CachedFixtureChannel();
            channel.setName(String.valueOf(typeOrColor));
            channel.setChannel(new FixtureChannel());
            channel.setMaxValue(255);
            channel.getCapabilities().add(cachedCapability);
            channels.add(channel);
        }

        return channels;
    }

    private static FixtureCapabilityValue value(FixtureCapability.FixtureCapabilityType type, FixtureCapability.FixtureCapabilityColor color, double valuePercentage) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setType(type);
        value.setColor(color);
        value.setValuePercentage(valuePercentage);
        return value;
    }

    private static double percentage(List<FixtureCapabilityValue> values, FixtureCapability.FixtureCapabilityColor color) {
        return values.stream().filter(value -> value.getColor() == color).reduce((first, second) -> second).orElseThrow().getValuePercentage();
    }

    @Test
    void lightsTheWhitePartOfTheColorsOnTheWhiteLeds() {
        List<CachedFixtureChannel> rgbw = fixture(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue, FixtureCapability.FixtureCapabilityColor.White);
        List<FixtureCapabilityValue> values = List.of(
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Red, 1),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Green, 0.6),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Blue, 0.4));

        List<FixtureCapabilityValue> played = EmitterColors.getEmitterValues(values, rgbw, true);
        assertEquals(0.6, percentage(played, FixtureCapability.FixtureCapabilityColor.Red), 1e-12);
        assertEquals(0.2, percentage(played, FixtureCapability.FixtureCapabilityColor.Green), 1e-12);
        assertEquals(0, percentage(played, FixtureCapability.FixtureCapabilityColor.Blue), 1e-12);
        assertEquals(0.4, percentage(played, FixtureCapability.FixtureCapabilityColor.White), 1e-12);

        // not asked for, a white of the step itself, a fixture without a white: as they are
        assertSame(values, EmitterColors.getEmitterValues(values, rgbw, false));
        List<FixtureCapabilityValue> ownWhite = new ArrayList<>(values);
        ownWhite.add(value(COLOR, FixtureCapability.FixtureCapabilityColor.White, 0.1));
        assertSame(ownWhite, EmitterColors.getEmitterValues(ownWhite, rgbw, true));
        assertSame(values, EmitterColors.getEmitterValues(values, fixture(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue), true));
    }

    @Test
    void playsTheColorTemperatureOnAWarmAndAColdWhite() {
        List<CachedFixtureChannel> bicolor = fixture(FixtureCapability.FixtureCapabilityType.Intensity, FixtureCapability.FixtureCapabilityColor.WarmWhite, FixtureCapability.FixtureCapabilityColor.ColdWhite);

        List<FixtureCapabilityValue> warm = EmitterColors.getEmitterValues(List.of(value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, 0.25)), bicolor, false);
        assertEquals(1, percentage(warm, FixtureCapability.FixtureCapabilityColor.WarmWhite));
        assertEquals(0.5, percentage(warm, FixtureCapability.FixtureCapabilityColor.ColdWhite));

        List<FixtureCapabilityValue> cold = EmitterColors.getEmitterValues(List.of(value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, 1)), bicolor, false);
        assertEquals(0, percentage(cold, FixtureCapability.FixtureCapabilityColor.WarmWhite));
        assertEquals(1, percentage(cold, FixtureCapability.FixtureCapabilityColor.ColdWhite));

        // a warm white of the step itself wins, a fixture with a channel of its own for the temperature uses it
        List<FixtureCapabilityValue> ownWarm = EmitterColors.getEmitterValues(List.of(
                value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, 1),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.WarmWhite, 0.3)), bicolor, false);
        assertEquals(0.3, percentage(ownWarm, FixtureCapability.FixtureCapabilityColor.WarmWhite));
        assertEquals(3, ownWarm.size());

        List<FixtureCapabilityValue> temperature = List.of(value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, 0.5));
        assertSame(temperature, EmitterColors.getEmitterValues(temperature, fixture(FixtureCapability.FixtureCapabilityType.ColorTemperature, FixtureCapability.FixtureCapabilityColor.WarmWhite, FixtureCapability.FixtureCapabilityColor.ColdWhite), false));
    }

    @Test
    void lightsTheAmberPartOfTheColorsOnTheAmberLedsAfterTheWhiteOne() {
        List<CachedFixtureChannel> rgbwa = fixture(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue, FixtureCapability.FixtureCapabilityColor.White, FixtureCapability.FixtureCapabilityColor.Amber);
        List<FixtureCapabilityValue> values = List.of(
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Red, 1),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Green, 0.6),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Blue, 0.2));

        // white 0.2, then of (0.8, 0.4, 0) the amber part: all of the green with a third more red
        List<FixtureCapabilityValue> played = EmitterColors.getEmitterValues(values, rgbwa, true);
        assertEquals(List.of(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue, FixtureCapability.FixtureCapabilityColor.White, FixtureCapability.FixtureCapabilityColor.Amber),
                played.stream().map(FixtureCapabilityValue::getColor).toList());
        assertEquals(0.8 - 0.4 / 0.75, percentage(played, FixtureCapability.FixtureCapabilityColor.Red), 1e-12);
        assertEquals(0, percentage(played, FixtureCapability.FixtureCapabilityColor.Green), 1e-12);
        assertEquals(0, percentage(played, FixtureCapability.FixtureCapabilityColor.Blue), 1e-12);
        assertEquals(0.2, percentage(played, FixtureCapability.FixtureCapabilityColor.White), 1e-12);
        assertEquals(0.4 / 0.75, percentage(played, FixtureCapability.FixtureCapabilityColor.Amber), 1e-12);
        // the colors of the step stay as they are
        assertEquals(1, values.get(0).getValuePercentage());

        // an amber set by the step itself wins
        List<FixtureCapabilityValue> ownAmber = List.of(
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Red, 1),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Green, 0.6),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Blue, 0.4),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Amber, 0.1));
        List<FixtureCapabilityValue> rgba = EmitterColors.getEmitterValues(ownAmber, fixture(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue, FixtureCapability.FixtureCapabilityColor.Amber), true);
        assertEquals(1, percentage(rgba, FixtureCapability.FixtureCapabilityColor.Red), 1e-12);
        assertEquals(0.6, percentage(rgba, FixtureCapability.FixtureCapabilityColor.Green), 1e-12);
        assertEquals(0.4, percentage(rgba, FixtureCapability.FixtureCapabilityColor.Blue), 1e-12);
        assertEquals(0.1, percentage(rgba, FixtureCapability.FixtureCapabilityColor.Amber), 1e-12);
    }

    @Test
    void showsTheColorTemperatureAsAColorOnAFixtureWithRedGreenAndBlueOnly() {
        List<CachedFixtureChannel> rgb = fixture(FixtureCapability.FixtureCapabilityColor.Red, FixtureCapability.FixtureCapabilityColor.Green, FixtureCapability.FixtureCapabilityColor.Blue);
        double[][] expected = {{0, 1, 0.6, 0.3}, {0.5, 1, 1, 1}, {0.75, 0.9, 0.94, 1}, {1, 0.8, 0.88, 1}};

        for (double[] temperature : expected) {
            List<FixtureCapabilityValue> played = EmitterColors.getEmitterValues(List.of(value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, temperature[0])), rgb, false);

            assertEquals(4, played.size());
            assertEquals(FixtureCapability.FixtureCapabilityType.ColorTemperature, played.get(0).getType());
            assertEquals(temperature[1], percentage(played, FixtureCapability.FixtureCapabilityColor.Red), 1e-12);
            assertEquals(temperature[2], percentage(played, FixtureCapability.FixtureCapabilityColor.Green), 1e-12);
            assertEquals(temperature[3], percentage(played, FixtureCapability.FixtureCapabilityColor.Blue), 1e-12);
        }

        // a color of the step itself wins
        List<FixtureCapabilityValue> ownBlue = List.of(
                value(FixtureCapability.FixtureCapabilityType.ColorTemperature, null, 0),
                value(COLOR, FixtureCapability.FixtureCapabilityColor.Blue, 0.5));
        List<FixtureCapabilityValue> played = EmitterColors.getEmitterValues(ownBlue, rgb, false);
        assertEquals(2, played.size());
        assertEquals(0.5, percentage(played, FixtureCapability.FixtureCapabilityColor.Blue), 1e-12);
    }
}
