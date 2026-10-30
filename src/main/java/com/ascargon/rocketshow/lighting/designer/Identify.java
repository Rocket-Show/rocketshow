package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

/**
 * What a fixture being identified from the fixture pool of the designer plays on top of the presets, so it can be told
 * apart on the stage: it blinks at full white, its shutter and its iris open and its color wheels on their open slot.
 * Whatever else the presets do with it stays, e.g. where it points to. The designer identifies it the very same way
 * (identify.ts).
 */
final class Identify {

    // how long a fixture being identified stays lit, and then dark, while it blinks
    static final double BLINK_MILLIS = 500;

    // the colors lit to make a fixture being identified white
    private static final List<FixtureCapability.FixtureCapabilityColor> COLORS = List.of(
            FixtureCapability.FixtureCapabilityColor.Red,
            FixtureCapability.FixtureCapabilityColor.Green,
            FixtureCapability.FixtureCapabilityColor.Blue,
            FixtureCapability.FixtureCapabilityColor.White,
            FixtureCapability.FixtureCapabilityColor.WarmWhite,
            FixtureCapability.FixtureCapabilityColor.ColdWhite
    );

    private Identify() {
    }

    private static FixtureCapabilityValue capabilityValue(FixtureCapability.FixtureCapabilityType type, double valuePercentage, FixtureCapability.FixtureCapabilityColor color) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setType(type);
        value.setValuePercentage(valuePercentage);
        value.setColor(color);

        return value;
    }

    // is a fixture being identified lit at the passed time (or dark)? It blinks from the start of the clock on.
    static boolean isLit(double timeMillis) {
        return ((Math.floor(timeMillis / BLINK_MILLIS) % 2) + 2) % 2 == 0;
    }

    static List<FixtureCapabilityValue> getCapabilityValues(CachedFixture cachedFixture, double timeMillis) {
        double level = isLit(timeMillis) ? 1 : 0;
        List<FixtureCapabilityValue> values = new ArrayList<>();

        values.add(capabilityValue(FixtureCapability.FixtureCapabilityType.Intensity, level, null));

        for (FixtureCapability.FixtureCapabilityColor color : COLORS) {
            values.add(capabilityValue(FixtureCapability.FixtureCapabilityType.ColorIntensity, level, color));
        }

        values.add(capabilityValue(FixtureCapability.FixtureCapabilityType.ShutterStrobe, 0, null));
        values.add(capabilityValue(FixtureCapability.FixtureCapabilityType.Iris, 1, null));

        List<String> wheels = new ArrayList<>();

        for (CachedFixtureChannel channel : cachedFixture.getChannels()) {
            for (CachedFixtureCapability channelCapability : channel.getCapabilities()) {
                boolean open = channelCapability.isWheelIsColor()
                        && !channelCapability.getWheelSlots().isEmpty()
                        && channelCapability.getWheelSlots().stream().allMatch(slot -> slot != null && slot.getType() == FixtureWheelSlot.FixtureWheelSlotType.Open);

                if (open && !wheels.contains(channelCapability.getWheelName())) {
                    wheels.add(channelCapability.getWheelName());

                    FixtureCapabilityValue slot = new FixtureCapabilityValue();
                    slot.setType(FixtureCapability.FixtureCapabilityType.WheelSlot);
                    slot.setWheel(channelCapability.getWheelName());
                    slot.setSlotNumber(channelCapability.getCapability().getSlotNumber());
                    slot.setProfileUuid(cachedFixture.getProfile().getUuid());
                    values.add(slot);
                }
            }
        }

        return values;
    }
}
