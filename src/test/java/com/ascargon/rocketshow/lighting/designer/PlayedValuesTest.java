package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The values of a step a preset plays: none turned off in the preset editor of the designer.
 */
class PlayedValuesTest {

    private static FixtureCapabilityValue capability(Boolean deactivated) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setDeactivated(deactivated);
        return value;
    }

    private static FixtureChannelValue channel(Boolean deactivated) {
        FixtureChannelValue value = new FixtureChannelValue();
        value.setDeactivated(deactivated);
        return value;
    }

    @Test
    void playsAllValuesWhileNoneIsOff() {
        List<FixtureCapabilityValue> values = List.of(capability(null), capability(false));

        assertSame(values, PlayedValues.ofCapabilities(values));
        assertNull(PlayedValues.ofChannels(null));
    }

    @Test
    void leavesTheValuesTurnedOffOut() {
        FixtureCapabilityValue on = capability(null);
        FixtureChannelValue channelOn = channel(false);

        assertEquals(List.of(on), PlayedValues.ofCapabilities(List.of(capability(true), on)));
        assertEquals(List.of(channelOn), PlayedValues.ofChannels(List.of(channelOn, channel(true))));
    }

    @Test
    void copiesWhetherAValueIsOff() {
        assertEquals(true, capability(true).copy().getDeactivated());
    }
}
