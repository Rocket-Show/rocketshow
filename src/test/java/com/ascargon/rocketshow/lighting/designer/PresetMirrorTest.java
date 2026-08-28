package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These mirror the specs of the Preset model in the Rocket Show Designer. A show has to
 * look the same on stage as it did while it was written, so both sides run the same
 * cases.
 */
class PresetMirrorTest {

    // a preset mirroring the passed axes
    private Preset preset(boolean mirrorPan, boolean mirrorTilt) {
        Preset mirrored = new Preset();
        mirrored.setMirrorPan(mirrorPan);
        mirrored.setMirrorTilt(mirrorTilt);

        return mirrored;
    }

    @Test
    void presetWhichMirrorsNothingPointsAtTheWrittenPosition() {
        Preset plain = preset(false, false);

        assertEquals(0.25, plain.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Pan, 0.25));
        assertEquals(0.25, plain.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Tilt, 0.25));
    }

    @Test
    void mirroredPanIsFoldedAndTheTiltIsLeftAlone() {
        Preset mirrored = preset(true, false);

        assertEquals(0.75, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Pan, 0.25));
        assertEquals(0.25, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Tilt, 0.25));
    }

    @Test
    void mirroredTiltIsFoldedAndThePanIsLeftAlone() {
        Preset mirrored = preset(false, true);

        assertEquals(0.25, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Pan, 0.25));
        assertEquals(0.75, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Tilt, 0.25));
    }

    @Test
    void mirroredAxisHoldsWhereItIsWrittenToTheMiddle() {
        Preset mirrored = preset(true, true);

        assertEquals(0.5, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Pan, 0.5));
        assertEquals(0.5, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Tilt, 0.5));
    }

    @Test
    void onlyThePanAndTheTiltOfAPresetAreMirrored() {
        Preset mirrored = preset(true, true);

        assertTrue(mirrored.mirrorsCapability(FixtureCapability.FixtureCapabilityType.Pan));
        assertTrue(mirrored.mirrorsCapability(FixtureCapability.FixtureCapabilityType.Tilt));
        assertFalse(mirrored.mirrorsCapability(FixtureCapability.FixtureCapabilityType.Intensity));
        assertEquals(0.25, mirrored.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Intensity, 0.25));
    }

    @Test
    void presetOfAProjectWrittenBeforeTheMirrorMirrorsNothing() {
        Preset old = new Preset();

        assertFalse(old.isMirrorPan());
        assertFalse(old.isMirrorTilt());
        assertEquals(0.25, old.getMirroredValuePercentage(FixtureCapability.FixtureCapabilityType.Pan, 0.25));
    }
}
