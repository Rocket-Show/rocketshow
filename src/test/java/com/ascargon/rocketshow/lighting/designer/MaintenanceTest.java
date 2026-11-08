package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The maintenance capabilities a preset asks for (reset, lamp on, lamp off), like maintenance.spec.ts of the designer
 * (the conformance cases play them on whole fixtures).
 */
class MaintenanceTest {

    private static FixtureCapability maintenance(int low, int high, String comment) {
        FixtureCapability capability = new FixtureCapability();
        capability.setType(FixtureCapability.FixtureCapabilityType.Maintenance);
        capability.setDmxRange(List.of(low, high));
        capability.setComment(comment);
        return capability;
    }

    private static List<CachedFixtureChannel> lamp() {
        FixtureCapability none = new FixtureCapability();
        none.setType(FixtureCapability.FixtureCapabilityType.NoFunction);
        none.setDmxRange(List.of(0, 9));

        CachedFixtureChannel channel = new CachedFixtureChannel();
        channel.setName("Control");
        channel.setChannel(new FixtureChannel());
        channel.setMaxValue(255);

        for (FixtureCapability capability : List.of(none, maintenance(10, 19, "Lamp on"), maintenance(20, 29, "Lamp off"), maintenance(30, 39, "Lamp off and reset"))) {
            CachedFixtureCapability cachedCapability = new CachedFixtureCapability();
            cachedCapability.setCapability(capability);
            channel.getCapabilities().add(cachedCapability);
        }

        return List.of(channel);
    }

    @Test
    void switchesTheLampOffAndFindsNothingForAnActionItDoesNotKnow() {
        List<CachedFixtureChannel> lamp = lamp();

        assertEquals(List.of(20, 29), Maintenance.getCapability(lamp, Maintenance.Action.of("lamp-off")).capability().getCapability().getDmxRange());
        assertEquals(List.of(10, 19), Maintenance.getCapability(lamp, Maintenance.Action.of("lamp-on")).capability().getCapability().getDmxRange());
        // only among other things
        assertEquals(List.of(30, 39), Maintenance.getCapability(lamp, Maintenance.Action.of("reset")).capability().getCapability().getDmxRange());

        assertNull(Maintenance.Action.of("unknown"));
        assertNull(Maintenance.Action.of(null));
        assertNull(Maintenance.getCapability(lamp, Maintenance.Action.of("unknown")));
    }
}
