package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the ends of the ranges of the fixture library are read, to play a capability set as a position whichever way
 * round a profile runs it. These mirror the cases of capability-position.spec.ts in the designer (the conformance cases
 * play the positions on whole fixtures).
 */
class CapabilityPositionsTest {

    @Test
    void readsTheNumbersOfTheFixtureLibraryWithTheirUnits() {
        assertEquals(10, CapabilityPositions.getPropertyRank("10deg"));
        assertEquals(12.5, CapabilityPositions.getPropertyRank(" 12.5% "));
        assertEquals(3200, CapabilityPositions.getPropertyRank("3200K"));
        assertEquals(2, CapabilityPositions.getPropertyRank("2m"));
        assertEquals(20, CapabilityPositions.getPropertyRank("20Hz"));
        assertEquals(-3, CapabilityPositions.getPropertyRank("-3"));
        assertEquals(1000, CapabilityPositions.getPropertyRank("1e3"));
    }

    @Test
    void comparesBeatsPerMinuteWithHertz() {
        assertEquals(2, CapabilityPositions.getPropertyRank("120bpm"));
    }

    @Test
    void placesTheWordsOfTheLibraryAmongTheNumbers() {
        assertTrue(CapabilityPositions.getPropertyRank("closed") < CapabilityPositions.getPropertyRank("narrow"));
        assertTrue(CapabilityPositions.getPropertyRank("narrow") < CapabilityPositions.getPropertyRank("1deg"));
        assertTrue(CapabilityPositions.getPropertyRank("wide") > CapabilityPositions.getPropertyRank("120deg"));
        assertTrue(CapabilityPositions.getPropertyRank("near") < CapabilityPositions.getPropertyRank("far"));
        assertTrue(CapabilityPositions.getPropertyRank("closed") < CapabilityPositions.getPropertyRank("open"));
        assertTrue(CapabilityPositions.getPropertyRank("off") < CapabilityPositions.getPropertyRank("low"));
        assertTrue(CapabilityPositions.getPropertyRank("low") < CapabilityPositions.getPropertyRank("high"));
        assertTrue(CapabilityPositions.getPropertyRank("warm") < CapabilityPositions.getPropertyRank("CTO"));
        assertTrue(CapabilityPositions.getPropertyRank("3200K") < CapabilityPositions.getPropertyRank("default"));
        assertTrue(CapabilityPositions.getPropertyRank("default") < CapabilityPositions.getPropertyRank("CTB"));
        assertTrue(CapabilityPositions.getPropertyRank("CTB") < CapabilityPositions.getPropertyRank("cold"));
        assertTrue(CapabilityPositions.getPropertyRank("stop") < CapabilityPositions.getPropertyRank("slow"));
        assertTrue(CapabilityPositions.getPropertyRank("slow") < CapabilityPositions.getPropertyRank("1Hz"));
        assertTrue(CapabilityPositions.getPropertyRank("fast") > CapabilityPositions.getPropertyRank("50Hz"));
    }

    @Test
    void readsNothingWhichIsNoPropertyOfTheLibrary() {
        assertNull(CapabilityPositions.getPropertyRank(null));
        assertNull(CapabilityPositions.getPropertyRank(""));
        assertNull(CapabilityPositions.getPropertyRank("0x10"));
        assertNull(CapabilityPositions.getPropertyRank("10 parsecs"));
        assertNull(CapabilityPositions.getPropertyRank("slow reverse"));
    }

    private static FixtureCapability capability(FixtureCapability.FixtureCapabilityType type) {
        FixtureCapability capability = new FixtureCapability();
        capability.setType(type);
        return capability;
    }

    @Test
    void tellsARangeRunningFromItsHighEndToItsLowEnd() {
        FixtureCapability zoom = capability(FixtureCapability.FixtureCapabilityType.Zoom);
        zoom.setAngleStart("wide");
        zoom.setAngleEnd("narrow");
        assertTrue(CapabilityPositions.isRangeReversed(zoom));

        zoom.setAngleStart("10deg");
        zoom.setAngleEnd("40deg");
        assertFalse(CapabilityPositions.isRangeReversed(zoom));

        FixtureCapability strobeSpeed = capability(FixtureCapability.FixtureCapabilityType.StrobeSpeed);
        strobeSpeed.setSpeedStart("20Hz");
        strobeSpeed.setSpeedEnd("60bpm");
        assertTrue(CapabilityPositions.isRangeReversed(strobeSpeed));

        FixtureCapability colorTemperature = capability(FixtureCapability.FixtureCapabilityType.ColorTemperature);
        colorTemperature.setColorTemperatureStart("6500K");
        colorTemperature.setColorTemperatureEnd("3200K");
        assertTrue(CapabilityPositions.isRangeReversed(colorTemperature));
    }

    @Test
    void runsARangeWhichDoesNotSayFromLowToHigh() {
        FixtureCapability zoom = capability(FixtureCapability.FixtureCapabilityType.Zoom);
        assertFalse(CapabilityPositions.isRangeReversed(zoom));

        zoom.setAngleStart("wide");
        assertFalse(CapabilityPositions.isRangeReversed(zoom));

        // the property of another capability says nothing about this one
        zoom.setAngleStart(null);
        zoom.setSpeedStart("fast");
        zoom.setSpeedEnd("slow");
        assertFalse(CapabilityPositions.isRangeReversed(zoom));
        assertFalse(CapabilityPositions.isRangeReversed(new FixtureCapability()));
    }

    @Test
    void knowsTheCapabilitiesSetAsAPosition() {
        for (String type : new String[]{"ShutterStrobe", "StrobeSpeed", "Zoom", "Focus", "Iris", "Frost", "ColorTemperature", "Prism"}) {
            assertTrue(CapabilityPositions.isPositionedType(FixtureCapability.FixtureCapabilityType.valueOf(type)), type);
        }

        for (String type : new String[]{"Intensity", "ColorIntensity", "Pan", "Tilt", "WheelSlot", "NoFunction"}) {
            assertFalse(CapabilityPositions.isPositionedType(FixtureCapability.FixtureCapabilityType.valueOf(type)), type);
        }
    }
}
