package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Positions of moving heads in degrees, fanned out and aimed at spots, like pan-tilt.spec.ts of the designer (the
 * conformance cases play them on whole fixtures).
 */
class PanTiltTest {

    private static CachedFixtureChannel channel(FixtureCapability.FixtureCapabilityType type, String angleStart, String angleEnd) {
        FixtureCapability capability = new FixtureCapability();
        capability.setType(type);
        capability.setAngleStart(angleStart);
        capability.setAngleEnd(angleEnd);

        CachedFixtureCapability cachedCapability = new CachedFixtureCapability();
        cachedCapability.setCapability(capability);

        CachedFixtureChannel channel = new CachedFixtureChannel();
        channel.setName(type.name());
        channel.setMaxValue(255);
        channel.getCapabilities().add(cachedCapability);

        return channel;
    }

    private static FixtureCapabilityValue value(FixtureCapability.FixtureCapabilityType type) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setType(type);
        return value;
    }

    @Test
    void readsHowFarAChannelTurnsItsFixture() {
        assertEquals(630, PanTilt.getAxisRange(channel(FixtureCapability.FixtureCapabilityType.Pan, "0deg", "630deg"), FixtureCapability.FixtureCapabilityType.Pan));
        assertEquals(-180, PanTilt.getAxisRange(channel(FixtureCapability.FixtureCapabilityType.Tilt, "90deg", "-90deg"), FixtureCapability.FixtureCapabilityType.Tilt));
        assertEquals(540, PanTilt.getAxisRange(channel(FixtureCapability.FixtureCapabilityType.Pan, null, null), FixtureCapability.FixtureCapabilityType.Pan));
        assertEquals(270, PanTilt.getAxisRange(channel(FixtureCapability.FixtureCapabilityType.Tilt, "10deg", "10deg"), FixtureCapability.FixtureCapabilityType.Tilt));
        assertNull(PanTilt.getAxisRange(channel(FixtureCapability.FixtureCapabilityType.Pan, null, null), FixtureCapability.FixtureCapabilityType.Tilt));
    }

    @Test
    void pointsEveryModelByTheSameDegreesAsFarAsItReaches() {
        CachedFixtureChannel wide = channel(FixtureCapability.FixtureCapabilityType.Pan, "0deg", "630deg");
        FixtureCapabilityValue degrees = value(FixtureCapability.FixtureCapabilityType.Pan);
        degrees.setValueDegrees(90d);

        assertEquals(255 * (0.5 + 90.0 / 630), PanTilt.getPanTiltChannelValue(degrees, wide, false, null), 1e-9);
        assertEquals(255 * (0.5 - 90.0 / 630), PanTilt.getPanTiltChannelValue(degrees, wide, true, null), 1e-9);

        degrees.setValueDegrees(1000d);
        assertEquals(255, PanTilt.getPanTiltChannelValue(degrees, wide, false, null));

        // a percentage like it always was
        FixtureCapabilityValue percentage = value(FixtureCapability.FixtureCapabilityType.Pan);
        percentage.setValuePercentage(0.3);
        assertEquals(255 * 0.3, PanTilt.getPanTiltChannelValue(percentage, wide, false, null), 1e-9);
        assertNull(PanTilt.getPanTiltChannelValue(value(FixtureCapability.FixtureCapabilityType.Pan), wide, false, null));
    }

    @Test
    void fansTheFixturesOfAPresetOut() {
        assertEquals(-30, PanTilt.getSpreadDegrees(60d, new PanTilt.Context(0, 3, null)));
        assertEquals(30, PanTilt.getSpreadDegrees(60d, new PanTilt.Context(2, 3, null)));
        assertEquals(0, PanTilt.getSpreadDegrees(60d, new PanTilt.Context(0, 1, null)));
        assertEquals(0, PanTilt.getSpreadDegrees(null, new PanTilt.Context(2, 3, null)));
        assertEquals(0, PanTilt.getSpreadDegrees(60d, null));
    }

    @Test
    void placesTheFixturesLikeThePreviewOfTheDesigner() {
        Project project = new Project();
        Fixture a = new Fixture();
        a.setUuid("a");
        Fixture c = new Fixture();
        c.setUuid("c");
        c.setPositioning("bottomBack");
        Fixture d = new Fixture();
        d.setUuid("d");
        d.setPositioning("manual");
        d.setPositionX(10);
        d.setRotationY(90);
        project.setFixtures(List.of(a, c, d));
        PresetFixture first = new PresetFixture();
        first.setFixtureUuid("a");
        PresetFixture second = new PresetFixture();
        second.setFixtureUuid("c");
        project.presetFixtures = List.of(first, second);

        Map<String, PanTilt.StagePlacement> placements = PanTilt.getStagePlacements(project);

        assertEquals(List.of(0.0, 367.0, 230.0), List.of(placements.get("a").position()[0], placements.get("a").position()[1], placements.get("a").position()[2]));
        assertEquals(180, placements.get("c").rotation()[0]);
        assertEquals(43, placements.get("c").position()[1]);
        assertEquals(10, placements.get("d").position()[0]);
        assertEquals(90, placements.get("d").rotation()[1]);
    }

    @Test
    void aimsAHangingHeadAtASpot() {
        PanTilt.StagePlacement hanging = new PanTilt.StagePlacement(new double[]{0, 405.4, 0}, new double[]{0, 0, 0});

        assertEquals(new PanTilt.Aim(0, 0), PanTilt.getAimDegrees(hanging, new double[]{0, 0, 0}));
        assertEquals(new PanTilt.Aim(0, 0), PanTilt.getAimDegrees(hanging, new double[]{0, 400, 0}));

        PanTilt.Aim front = PanTilt.getAimDegrees(hanging, new double[]{0, 0, 400});
        assertEquals(0, front.pan(), 1e-9);
        assertEquals(-45, front.tilt(), 1e-9);

        PanTilt.Aim side = PanTilt.getAimDegrees(hanging, new double[]{400, 0, 0});
        assertEquals(-90, side.pan(), 1e-9);
        assertEquals(45, side.tilt(), 1e-9);

        // a spot only where it knows where the fixture hangs, whatever the mirror says
        FixtureCapabilityValue aimed = value(FixtureCapability.FixtureCapabilityType.Pan);
        aimed.setAimX(400d);
        aimed.setAimY(0d);
        aimed.setAimZ(0d);
        aimed.setSpreadDegrees(20d);
        assertTrue(PanTilt.hasAim(aimed));
        assertNull(PanTilt.getPanTiltDegrees(aimed, 540, true, new PanTilt.Context(0, 2, null)));
        assertEquals(-80, PanTilt.getPanTiltDegrees(aimed, 540, true, new PanTilt.Context(1, 2, hanging)), 1e-9);

        aimed.setAimZ(null);
        assertFalse(PanTilt.hasAim(aimed));
    }

    @Test
    void findsWherePresetPointsAFixtureTheLastValueWinning() {
        FixtureCapabilityValue first = value(FixtureCapability.FixtureCapabilityType.Pan);
        first.setValueDegrees(10d);
        FixtureCapabilityValue other = value(FixtureCapability.FixtureCapabilityType.Pan);
        other.setValueDegrees(20d);
        other.setProfileUuid("other");
        FixtureCapabilityValue last = value(FixtureCapability.FixtureCapabilityType.Pan);
        last.setValueDegrees(30d);
        List<FixtureCapabilityValue> values = List.of(first, other, last, value(FixtureCapability.FixtureCapabilityType.Pan));

        assertEquals(30, PanTilt.getPresetPanTiltDegrees(values, FixtureCapability.FixtureCapabilityType.Pan, "profile", 540, false, null));
        assertEquals(-30, PanTilt.getPresetPanTiltDegrees(values, FixtureCapability.FixtureCapabilityType.Pan, "profile", 540, true, null));
        assertNull(PanTilt.getPresetPanTiltDegrees(values, FixtureCapability.FixtureCapabilityType.Tilt, "profile", 270, false, null));
    }

    // a value travelling from the one of the step before, like a preset shows it between two of its steps
    private static FixtureCapabilityValue travelling(FixtureCapabilityValue from, FixtureCapabilityValue to, double position) {
        FixtureCapabilityValue between = to.copy();
        between.setTransitionFrom(from);
        between.setTransitionPosition(position);
        return between;
    }

    @Test
    void tellsWhichValuesTravelForEachFixtureOnItsOwn() {
        FixtureCapabilityValue percentage = value(FixtureCapability.FixtureCapabilityType.Pan);
        percentage.setValuePercentage(0.3);
        FixtureCapabilityValue otherPercentage = value(FixtureCapability.FixtureCapabilityType.Pan);
        otherPercentage.setValuePercentage(0.6);
        FixtureCapabilityValue degrees = value(FixtureCapability.FixtureCapabilityType.Pan);
        degrees.setValueDegrees(10d);
        FixtureCapabilityValue fanned = value(FixtureCapability.FixtureCapabilityType.Pan);
        fanned.setValuePercentage(0.3);
        fanned.setSpreadDegrees(40d);

        // two percentages of the range alone travel like any other percentage
        assertFalse(PanTilt.isPanTiltTransition(percentage, otherPercentage));
        // degrees and a fan travel for each fixture
        assertTrue(PanTilt.isPanTiltTransition(percentage, degrees));
        assertTrue(PanTilt.isPanTiltTransition(percentage, fanned));
        // a value saying nothing is taken as soon as the transition starts
        assertFalse(PanTilt.isPanTiltTransition(value(FixtureCapability.FixtureCapabilityType.Pan), degrees));
    }

    @Test
    void opensTheFanForEachFixtureOnItsOwnBetweenTwoSteps() {
        FixtureCapabilityValue ahead = value(FixtureCapability.FixtureCapabilityType.Pan);
        ahead.setValueDegrees(0d);
        FixtureCapabilityValue fanned = value(FixtureCapability.FixtureCapabilityType.Pan);
        fanned.setValueDegrees(20d);
        fanned.setSpreadDegrees(120d);
        PanTilt.Context first = new PanTilt.Context(0, 3, null);
        PanTilt.Context middle = new PanTilt.Context(1, 3, null);
        PanTilt.Context last = new PanTilt.Context(2, 3, null);

        // halfway: the heads point halfway to the middle of the fan, the first and the last one halfway out to its sides
        assertEquals(-20, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 0.5), 540, false, first), 1e-9);
        assertEquals(10, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 0.5), 540, false, middle), 1e-9);
        assertEquals(40, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 0.5), 540, false, last), 1e-9);

        // from where they start to where they end up, mirrored with the preset
        assertEquals(0, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 0), 540, false, last));
        assertEquals(80, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 1), 540, false, last));
        assertEquals(-20, PanTilt.getPanTiltDegrees(travelling(ahead, fanned, 0.25), 540, true, last), 1e-9);

        // the channel follows the head, a percentage of the range included
        CachedFixtureChannel wide = channel(FixtureCapability.FixtureCapabilityType.Pan, "0deg", "630deg");
        FixtureCapabilityValue middlePercentage = value(FixtureCapability.FixtureCapabilityType.Pan);
        middlePercentage.setValuePercentage(0.5);
        assertEquals(255 * (0.5 + 40.0 / 630), PanTilt.getPanTiltChannelValue(travelling(middlePercentage, fanned, 0.5), wide, false, last), 1e-9);
    }

    @Test
    void turnsTheFixturesFromASpotToAPosition() {
        PanTilt.StagePlacement hanging = new PanTilt.StagePlacement(new double[]{0, 405.4, 0}, new double[]{0, 0, 0});
        FixtureCapabilityValue spot = value(FixtureCapability.FixtureCapabilityType.Tilt);
        spot.setAimX(0d);
        spot.setAimY(0d);
        spot.setAimZ(400d);
        FixtureCapabilityValue down = value(FixtureCapability.FixtureCapabilityType.Tilt);
        down.setValueDegrees(0d);

        // from 45° up to the front down to straight down
        assertEquals(-22.5, PanTilt.getPanTiltDegrees(travelling(spot, down, 0.5), 270, false, new PanTilt.Context(0, 1, hanging)), 1e-9);

        // without knowing where the fixture hangs, it turns to the position as soon as the transition starts
        assertNull(PanTilt.getPanTiltDegrees(travelling(spot, down, 0), 270, false, null));
        assertEquals(0, PanTilt.getPanTiltDegrees(travelling(spot, down, 0.01), 270, false, null));
        assertEquals(0, PanTilt.getPanTiltDegrees(travelling(down, spot, 0), 270, false, null));
        assertNull(PanTilt.getPanTiltDegrees(travelling(down, spot, 0.5), 270, false, null));
    }
}
