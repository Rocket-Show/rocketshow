package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The built-in programs of a fixture, picked by their number and run at a speed, like program.spec.ts of the designer
 * (the conformance cases play them on whole fixtures).
 */
class ProgramsTest {

    private static FixtureCapability range(int low, int high, FixtureCapability.FixtureCapabilityType type, String effectName) {
        FixtureCapability capability = new FixtureCapability();
        capability.setType(type);
        capability.setDmxRange(List.of(low, high));
        capability.setEffectName(effectName);
        return capability;
    }

    // a channel of the passed capabilities, cached like the designer service caches them
    private static CachedFixtureChannel channel(FixtureCapability... capabilities) {
        CachedFixtureChannel channel = new CachedFixtureChannel();
        channel.setName("Channel");
        channel.setChannel(new FixtureChannel());
        channel.setMaxValue(255);

        for (FixtureCapability capability : capabilities) {
            CachedFixtureCapability cachedCapability = new CachedFixtureCapability();
            cachedCapability.setCapability(capability);
            cachedCapability.setCenterValue((int) Math.floor((capability.getDmxRange().get(0) + capability.getDmxRange().get(1)) / 2f));
            channel.getCapabilities().add(cachedCapability);
        }

        return channel;
    }

    private static CachedFixtureChannel programs() {
        FixtureCapability auto3 = range(100, 255, FixtureCapability.FixtureCapabilityType.Effect, "Auto 3");
        auto3.setSpeedStart("fast");
        auto3.setSpeedEnd("slow");
        FixtureCapability auto1 = range(10, 39, FixtureCapability.FixtureCapabilityType.Effect, "Auto 1");
        auto1.setSpeedStart("slow");
        auto1.setSpeedEnd("fast");
        FixtureCapability sound1 = range(70, 99, FixtureCapability.FixtureCapabilityType.Effect, "Sound 1");
        sound1.setSoundControlled(true);

        return channel(
                range(0, 9, FixtureCapability.FixtureCapabilityType.NoFunction, null),
                auto3,
                auto1,
                range(40, 69, FixtureCapability.FixtureCapabilityType.Effect, "Auto 2"),
                sound1);
    }

    private static List<String> names(List<CachedFixtureCapability> programs) {
        return programs.stream().map(program -> program.getCapability().getEffectName()).toList();
    }

    @Test
    void numbersTheProgramsOfAChannelInTheOrderOfTheirValuesToTheSoundOnTheirOwn() {
        CachedFixtureChannel programs = programs();

        assertEquals(List.of("Auto 1", "Auto 2", "Auto 3"), names(Programs.getPrograms(programs, false)));
        assertEquals(List.of("Sound 1"), names(Programs.getPrograms(programs, true)));

        // a channel without programs to the sound picks among all of them
        CachedFixtureChannel auto = channel(range(10, 39, FixtureCapability.FixtureCapabilityType.Effect, "Auto 1"));
        assertEquals(List.of("Auto 1"), names(Programs.getPrograms(auto, true)));
    }

    @Test
    void runsAProgramAtItsSpeedNoneAsNoFunction() {
        CachedFixtureChannel programs = programs();

        // slow to fast within the range of the program, the other way round where it runs the other way
        assertEquals(24.5, Programs.getProgramChannelValue(programs, 1.0, false, 0.5), 1e-9);
        assertEquals(255, Programs.getProgramChannelValue(programs, 3.0, false, 0.0));
        // a program without speeds in its range is played in its middle
        assertEquals(54, Programs.getProgramChannelValue(programs, 2.0, false, 0.9));
        assertEquals(84, Programs.getProgramChannelValue(programs, 1.0, true, 0.5));
        // without a speed, in its middle as well
        assertEquals(24, Programs.getProgramChannelValue(programs, 1.0, false, null));
        assertEquals(4, Programs.getProgramChannelValue(programs, 0.0, false, 0.5));
        assertEquals(4, Programs.getProgramChannelValue(programs, null, false, 0.5));
        // a program the channel doesn't have
        assertNull(Programs.getProgramChannelValue(programs, 4.0, false, 0.5));
    }
}
