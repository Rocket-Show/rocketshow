package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void comparesTimesAndRoundsPerMinute() {
        assertEquals(0.5, CapabilityPositions.getPropertyRank("500ms"));
        assertEquals(2, CapabilityPositions.getPropertyRank("2s"));
        assertEquals(2, CapabilityPositions.getPropertyRank("120rpm"));
        assertEquals(2, CapabilityPositions.getPropertyRank("2m^3/min"));
        assertTrue(CapabilityPositions.getPropertyRank("instant") < CapabilityPositions.getPropertyRank("short"));
        assertTrue(CapabilityPositions.getPropertyRank("short") < CapabilityPositions.getPropertyRank("1ms"));
        assertTrue(CapabilityPositions.getPropertyRank("long") > CapabilityPositions.getPropertyRank("60s"));
        assertTrue(CapabilityPositions.getPropertyRank("off") < CapabilityPositions.getPropertyRank("weak"));
        assertTrue(CapabilityPositions.getPropertyRank("weak") < CapabilityPositions.getPropertyRank("strong"));
    }

    @Test
    void tellsAMovementGivenByTheTimeItTakesTheOtherWayRound() {
        FixtureCapability speed = capability(FixtureCapability.FixtureCapabilityType.PanTiltSpeed);
        speed.setDurationStart("0.1s");
        speed.setDurationEnd("20s");
        assertTrue(CapabilityPositions.isRangeReversed(speed));

        speed.setDurationStart("long");
        speed.setDurationEnd("short");
        assertFalse(CapabilityPositions.isRangeReversed(speed));

        // a speed says it rather than a time
        speed.setSpeedStart("slow");
        speed.setSpeedEnd("fast");
        speed.setDurationStart("0.1s");
        speed.setDurationEnd("20s");
        assertFalse(CapabilityPositions.isRangeReversed(speed));
    }

    @Test
    void tellsTheRangesOfTheFlashDurationTheBeamAngleAndTheFog() {
        FixtureCapability flash = capability(FixtureCapability.FixtureCapabilityType.StrobeDuration);
        flash.setDurationStart("5ms");
        flash.setDurationEnd("0.5s");
        assertFalse(CapabilityPositions.isRangeReversed(flash));

        FixtureCapability beam = capability(FixtureCapability.FixtureCapabilityType.BeamAngle);
        beam.setAngleStart("45deg");
        beam.setAngleEnd("4deg");
        assertTrue(CapabilityPositions.isRangeReversed(beam));

        FixtureCapability fog = capability(FixtureCapability.FixtureCapabilityType.Fog);
        fog.setFogOutputStart("strong");
        fog.setFogOutputEnd("weak");
        assertTrue(CapabilityPositions.isRangeReversed(fog));
    }

    @Test
    void playsTheZoomOnTheBeamAngleAndTheFogOutputOnTheFog() {
        assertEquals(List.of(FixtureCapability.FixtureCapabilityType.Zoom, FixtureCapability.FixtureCapabilityType.BeamAngle),
                CapabilityPositions.getPlayingTypes(FixtureCapability.FixtureCapabilityType.Zoom));
        assertEquals(List.of(FixtureCapability.FixtureCapabilityType.FogOutput, FixtureCapability.FixtureCapabilityType.Fog),
                CapabilityPositions.getPlayingTypes(FixtureCapability.FixtureCapabilityType.FogOutput));
        assertFalse(CapabilityPositions.isPositionedType(FixtureCapability.FixtureCapabilityType.BeamAngle));
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
        for (String type : new String[]{"ShutterStrobe", "StrobeSpeed", "StrobeDuration", "Zoom", "Focus", "Iris", "Frost", "ColorTemperature", "Prism", "PanTiltSpeed", "FogOutput"}) {
            assertTrue(CapabilityPositions.isPositionedType(FixtureCapability.FixtureCapabilityType.valueOf(type)), type);
        }

        for (String type : new String[]{"Intensity", "ColorIntensity", "Pan", "Tilt", "WheelSlot", "NoFunction"}) {
            assertFalse(CapabilityPositions.isPositionedType(FixtureCapability.FixtureCapabilityType.valueOf(type)), type);
        }
    }
}
