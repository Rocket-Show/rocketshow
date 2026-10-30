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

    // a channel of the passed capabilities, cached like the designer service caches them
    private static CachedFixtureChannel channel(List<FixtureCapability> capabilities) {
        CachedFixtureChannel channel = new CachedFixtureChannel();
        channel.setName("Channel");
        channel.setMaxValue(255);

        for (FixtureCapability capability : capabilities) {
            CachedFixtureCapability cachedCapability = new CachedFixtureCapability();
            cachedCapability.setCapability(capability);

            if (capability.getDmxRange() != null && capability.getDmxRange().size() == 2) {
                cachedCapability.setCenterValue((int) Math.floor((capability.getDmxRange().get(0) + capability.getDmxRange().get(1)) / 2f));
            }

            channel.getCapabilities().add(cachedCapability);
        }

        return channel;
    }

    private static FixtureCapability range(int low, int high, FixtureCapability.FixtureCapabilityType type) {
        FixtureCapability capability = capability(type);
        capability.setDmxRange(List.of(low, high));
        return capability;
    }

    @Test
    void turnsEitherWayAndStopsInTheMiddle() {
        FixtureCapability anticlockwise = range(0, 63, FixtureCapability.FixtureCapabilityType.WheelSlotRotation);
        anticlockwise.setSpeedStart("fast CCW");
        anticlockwise.setSpeedEnd("slow CCW");
        FixtureCapability stop = range(64, 67, FixtureCapability.FixtureCapabilityType.WheelSlotRotation);
        stop.setSpeed("stop");
        FixtureCapability clockwise = range(68, 127, FixtureCapability.FixtureCapabilityType.WheelSlotRotation);
        clockwise.setSpeedStart("slow CW");
        clockwise.setSpeedEnd("fast CW");
        FixtureCapability indexed = range(128, 255, FixtureCapability.FixtureCapabilityType.WheelSlotRotation);
        indexed.setAngleStart("0deg");
        indexed.setAngleEnd("360deg");
        CachedFixtureChannel gobo = channel(List.of(anticlockwise, stop, clockwise, indexed));

        assertEquals(0, CapabilityPositions.getPositionedChannelValue(gobo, FixtureCapability.FixtureCapabilityType.WheelSlotRotation, 0, null));
        assertEquals(31.5, CapabilityPositions.getRotationChannelValue(gobo, FixtureCapability.FixtureCapabilityType.WheelSlotRotation, 0.25));
        assertEquals(65, CapabilityPositions.getRotationChannelValue(gobo, FixtureCapability.FixtureCapabilityType.WheelSlotRotation, 0.5));
        assertEquals(127, CapabilityPositions.getRotationChannelValue(gobo, FixtureCapability.FixtureCapabilityType.WheelSlotRotation, 1));

        // turning both ways on one range, the other way round
        FixtureCapability across = capability(FixtureCapability.FixtureCapabilityType.PrismRotation);
        across.setSpeedStart("fast CW");
        across.setSpeedEnd("fast CCW");
        CachedFixtureChannel prism = channel(List.of(across));
        assertEquals(0, CapabilityPositions.getRotationChannelValue(prism, FixtureCapability.FixtureCapabilityType.PrismRotation, 1));
        assertEquals(127.5, CapabilityPositions.getRotationChannelValue(prism, FixtureCapability.FixtureCapabilityType.PrismRotation, 0.5));
        assertEquals(255, CapabilityPositions.getRotationChannelValue(prism, FixtureCapability.FixtureCapabilityType.PrismRotation, 0));

        // stopped with no function, not turning anticlockwise at all
        FixtureCapability turning = range(10, 255, FixtureCapability.FixtureCapabilityType.Rotation);
        turning.setSpeedStart("slow CW");
        turning.setSpeedEnd("fast CW");
        CachedFixtureChannel rotation = channel(List.of(range(0, 9, FixtureCapability.FixtureCapabilityType.NoFunction), turning));
        assertEquals(4, CapabilityPositions.getRotationChannelValue(rotation, FixtureCapability.FixtureCapabilityType.Rotation, 0.5));
        assertNull(CapabilityPositions.getRotationChannelValue(rotation, FixtureCapability.FixtureCapabilityType.Rotation, 0));
        assertNull(CapabilityPositions.getRotationChannelValue(channel(List.of(indexed)), FixtureCapability.FixtureCapabilityType.WheelSlotRotation, 1));
        assertEquals(0, CapabilityPositions.getRotationRank("stop"));
        assertTrue(CapabilityPositions.getRotationRank("-10rpm") < 0);
    }

    @Test
    void putsInTheBladeTheValueNames() {
        FixtureCapability top = range(0, 127, FixtureCapability.FixtureCapabilityType.BladeInsertion);
        top.setBlade("Top");
        top.setInsertionStart("out");
        top.setInsertionEnd("in");
        FixtureCapability bottom = range(128, 255, FixtureCapability.FixtureCapabilityType.BladeInsertion);
        bottom.setBlade("Bottom");
        bottom.setInsertionStart("100%");
        bottom.setInsertionEnd("0%");
        CachedFixtureChannel blades = channel(List.of(top, bottom));

        assertEquals(127, CapabilityPositions.getPositionedChannelValue(blades, FixtureCapability.FixtureCapabilityType.BladeInsertion, 1, "Top"));
        assertEquals(128, CapabilityPositions.getPositionedChannelValue(blades, FixtureCapability.FixtureCapabilityType.BladeInsertion, 1, "Bottom"));
        assertNull(CapabilityPositions.getPositionedChannelValue(blades, FixtureCapability.FixtureCapabilityType.BladeInsertion, 1, "Left"));
        assertEquals(0, CapabilityPositions.getPositionedChannelValue(blades, FixtureCapability.FixtureCapabilityType.BladeInsertion, 0, null));
    }

    @Test
    void pulsesTheIrisAndShakesTheGoboOverTheRestOfTheirChannel() {
        FixtureCapability iris = range(0, 199, FixtureCapability.FixtureCapabilityType.Iris);
        FixtureCapability pulse = range(200, 255, FixtureCapability.FixtureCapabilityType.IrisEffect);
        pulse.setSpeedStart("slow");
        pulse.setSpeedEnd("fast");
        CachedFixtureChannel irisChannel = channel(List.of(iris, pulse));

        assertTrue(CapabilityPositions.isOverridingType(FixtureCapability.FixtureCapabilityType.IrisEffect));
        assertNull(CapabilityPositions.getPositionedChannelValue(irisChannel, FixtureCapability.FixtureCapabilityType.IrisEffect, 0, null));
        assertEquals(255, CapabilityPositions.getPositionedChannelValue(irisChannel, FixtureCapability.FixtureCapabilityType.IrisEffect, 1, null));

        // a gobo shaking on its own, and the whole wheel shaking, named after its channel
        FixtureCapability slot = range(10, 59, FixtureCapability.FixtureCapabilityType.WheelShake);
        slot.setSlotNumber(2d);
        slot.setShakeSpeedStart("slow");
        slot.setShakeSpeedEnd("fast");
        FixtureCapability wheel = range(60, 255, FixtureCapability.FixtureCapabilityType.WheelShake);
        wheel.setShakeSpeedStart("fast");
        wheel.setShakeSpeedEnd("slow");
        CachedFixtureChannel goboChannel = channel(List.of(slot, wheel));
        goboChannel.getCapabilities().get(0).setWheelName("Channel");

        assertEquals(59, CapabilityPositions.getShakeChannelValue(goboChannel, "Channel", 2d, 1));
        assertEquals(60, CapabilityPositions.getShakeChannelValue(goboChannel, "Channel", 3d, 1));
        assertNull(CapabilityPositions.getShakeChannelValue(goboChannel, "Channel", 2d, 0));
        assertNull(CapabilityPositions.getShakeChannelValue(goboChannel, "Other", 2d, 1));
    }
}
