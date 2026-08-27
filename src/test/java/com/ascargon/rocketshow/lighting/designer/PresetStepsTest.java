package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These mirror the specs of PresetStepService in the Rocket Show Designer. A show has to
 * look the same on stage as it did while it was written, so both sides run the same
 * cases.
 */
class PresetStepsTest {

    // a step driving one channel to the passed value, reached at startMillis
    private PresetStep step(long startMillis, double value, long transitionMillis) {
        PresetStep step = new PresetStep();
        step.setUuid("step-" + startMillis);
        step.setStartMillis(startMillis);
        step.setTransitionMillis(transitionMillis);

        FixtureChannelValue channelValue = new FixtureChannelValue();
        channelValue.setChannelName("dimmer");
        channelValue.setProfileUuid("profile");
        channelValue.setValue(value);
        step.getFixtureChannelValues().add(channelValue);

        return step;
    }

    private Preset preset(PresetStep... steps) {
        Preset preset = new Preset();
        preset.setSteps(new ArrayList<>(Arrays.asList(steps)));

        return preset;
    }

    private double dimmerAt(Preset preset, long timeMillis) {
        PresetStepState state = PresetSteps.getStateAtMillis(preset, timeMillis, false);

        for (FixtureChannelValue channelValue : state.getFixtureChannelValues()) {
            if ("dimmer".equals(channelValue.getChannelName())) {
                return channelValue.getValue();
            }
        }

        throw new IllegalStateException("The state does not drive the dimmer");
    }

    @Test
    void singleStepAppliesAtAnyTime() {
        Preset single = preset(step(0, 100, 0));

        assertEquals(100, dimmerAt(single, -500));
        assertEquals(100, dimmerAt(single, 0));
        assertEquals(100, dimmerAt(single, 10000));
    }

    @Test
    void presetWithoutStepsFallsBackToItsOwnValues() {
        Preset old = new Preset();
        FixtureChannelValue channelValue = new FixtureChannelValue();
        channelValue.setChannelName("dimmer");
        channelValue.setProfileUuid("profile");
        channelValue.setValue(42d);
        old.setFixtureChannelValues(new ArrayList<>(List.of(channelValue)));

        assertEquals(42, dimmerAt(old, 0));
    }

    @Test
    void firstStepIsHeldBeforeItIsReached() {
        Preset sequence = preset(step(1000, 100, 0), step(2000, 200, 0));

        assertEquals(100, dimmerAt(sequence, 0));
        assertEquals(100, dimmerAt(sequence, 999));
    }

    @Test
    void stepWithoutTransitionIsJumpedTo() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));

        assertEquals(100, dimmerAt(sequence, 999));
        assertEquals(200, dimmerAt(sequence, 1000));
    }

    @Test
    void stepIsReachedAtItsOwnStart() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 400));

        // the transition runs over the 400 ms in front of the step
        assertEquals(100, dimmerAt(sequence, 600));
        assertEquals(150, dimmerAt(sequence, 800));
        assertEquals(200, dimmerAt(sequence, 1000));
    }

    @Test
    void transitionDoesNotReachBackPastTheStepItStartsFrom() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 0), step(1000, 300, 5000));

        assertEquals(200, dimmerAt(sequence, 500));
        assertEquals(250, dimmerAt(sequence, 750));
    }

    @Test
    void lastStepIsHeldWithoutLooping() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));

        assertEquals(200, dimmerAt(sequence, 100000));
    }

    @Test
    void lastStepOfALoopLastsAsLongAsTheOneBeforeIt() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 0), step(1000, 300, 0));

        assertEquals(1500, PresetSteps.getStepsLoopMillis(sequence));
    }

    @Test
    void loopingSequenceStartsOver() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 0), step(1000, 300, 0));
        sequence.setStepsLoop(true);

        assertEquals(300, dimmerAt(sequence, 1499));
        assertEquals(100, dimmerAt(sequence, 1500));
        assertEquals(200, dimmerAt(sequence, 2000));
    }

    @Test
    void loopTravelsFromTheLastStepBackIntoTheFirst() {
        Preset sequence = preset(step(0, 100, 1000), step(1000, 300, 0));
        sequence.setStepsLoop(true);
        sequence.setStepsLoopMillis(2000L);

        assertEquals(300, dimmerAt(sequence, 1000));
        assertEquals(200, dimmerAt(sequence, 1500));
    }

    @Test
    void sequenceCanBeRunWithoutThePresetLooping() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 0), step(1000, 300, 0));

        assertEquals(200, PresetSteps.getStateAtMillis(sequence, 2000, true).getFixtureChannelValues().get(0).getValue());
    }

    @Test
    void valueOnlyOneStepCarriesIsHeld() {
        PresetStep from = step(0, 100, 0);
        PresetStep to = step(1000, 200, 1000);

        FixtureChannelValue onlyInFrom = new FixtureChannelValue();
        onlyInFrom.setChannelName("strobe");
        onlyInFrom.setProfileUuid("profile");
        onlyInFrom.setValue(50d);
        from.getFixtureChannelValues().add(onlyInFrom);

        FixtureChannelValue onlyInTo = new FixtureChannelValue();
        onlyInTo.setChannelName("zoom");
        onlyInTo.setProfileUuid("profile");
        onlyInTo.setValue(70d);
        to.getFixtureChannelValues().add(onlyInTo);

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 500, false);

        assertEquals(50, valueOf(state, "strobe"));
        assertEquals(70, valueOf(state, "zoom"));
    }

    private double valueOf(PresetStepState state, String channelName) {
        for (FixtureChannelValue channelValue : state.getFixtureChannelValues()) {
            if (channelName.equals(channelValue.getChannelName())) {
                return channelValue.getValue();
            }
        }

        throw new IllegalStateException("The state does not drive " + channelName);
    }

    @Test
    void capabilityValueIsInterpolatedOverTheTransition() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);

        from.getFixtureCapabilityValues().add(colorIntensity(0.2));
        to.getFixtureCapabilityValues().add(colorIntensity(1));

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 500, false);

        assertEquals(0.6, state.getFixtureCapabilityValues().get(0).getValuePercentage(), 1e-10);
    }

    private FixtureCapabilityValue colorIntensity(double valuePercentage) {
        FixtureCapabilityValue capabilityValue = new FixtureCapabilityValue();
        capabilityValue.setType(FixtureCapability.FixtureCapabilityType.ColorIntensity);
        capabilityValue.setColor(FixtureCapability.FixtureCapabilityColor.Red);
        capabilityValue.setValuePercentage(valuePercentage);

        return capabilityValue;
    }

    private FixtureCapabilityValue wheelSlot(int slotNumber) {
        FixtureCapabilityValue capabilityValue = new FixtureCapabilityValue();
        capabilityValue.setType(FixtureCapability.FixtureCapabilityType.WheelSlot);
        capabilityValue.setWheel("Color Wheel");
        capabilityValue.setSlotNumber(slotNumber);

        return capabilityValue;
    }

    @Test
    void wheelTurnsAsSoonAsTheTransitionStarts() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);

        from.getFixtureCapabilityValues().add(wheelSlot(1));
        to.getFixtureCapabilityValues().add(wheelSlot(4));

        Preset sequence = preset(from, to);

        assertEquals(1, (int) PresetSteps.getStateAtMillis(sequence, 0, false).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, (int) PresetSteps.getStateAtMillis(sequence, 500, false).getFixtureCapabilityValues().get(0).getSlotNumber());
    }

    @Test
    void wheelIsHeldUntilTheEndOfASnappingTransition() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);
        to.setTransitionCurve("snap");

        from.getFixtureCapabilityValues().add(wheelSlot(1));
        to.getFixtureCapabilityValues().add(wheelSlot(4));

        Preset sequence = preset(from, to);

        assertEquals(1, (int) PresetSteps.getStateAtMillis(sequence, 500, false).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, (int) PresetSteps.getStateAtMillis(sequence, 1000, false).getFixtureCapabilityValues().get(0).getSlotNumber());
    }

    @Test
    void transitionIsShapedByItsCurve() {
        Preset sequence = preset(step(0, 0, 0), step(1000, 100, 1000));
        sequence.getSteps().get(1).setTransitionCurve("ease-in");

        // half way through an ease-in, a quarter of the distance is covered
        assertEquals(25, dimmerAt(sequence, 500), 1e-10);
    }

    @Test
    void effectRunsFullyUnlessAStepSaysOtherwise() {
        Preset sequence = preset(step(0, 0, 0), step(1000, 100, 0));

        assertEquals(1, PresetSteps.getStateAtMillis(sequence, 0, false).getEffectAmount("effect"));
    }

    @Test
    void effectAmountIsInterpolated() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);

        from.getEffectAmounts().add(effectAmount(0));
        to.getEffectAmounts().add(effectAmount(1));

        assertEquals(0.5, PresetSteps.getStateAtMillis(preset(from, to), 500, false).getEffectAmount("effect"), 1e-10);
    }

    private PresetStepEffectAmount effectAmount(double amount) {
        PresetStepEffectAmount effectAmount = new PresetStepEffectAmount();
        effectAmount.setEffectUuid("effect");
        effectAmount.setAmount(amount);

        return effectAmount;
    }

    @Test
    void presetWhichWasNotAskedToChaseDoesNotChase() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));

        assertFalse(PresetSteps.stepsArePhased(sequence));
        assertEquals(0, PresetSteps.getStepsPhasingMillis(sequence, 3, 4));
    }

    @Test
    void chaseShiftsEachFixtureByAFixedTime() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));
        sequence.setStepsPhasingMillis(250);

        assertTrue(PresetSteps.stepsArePhased(sequence));
        assertEquals(0, PresetSteps.getStepsPhasingMillis(sequence, 0, 4));
        assertEquals(500, PresetSteps.getStepsPhasingMillis(sequence, 2, 4));
    }

    @Test
    void chaseSpreadsWholePassesOverAllFixtures() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 0), step(1000, 300, 0));
        sequence.setStepsPhasingMode("spread");
        sequence.setStepsPhasingCycles(1);

        // one pass is 1500 ms long and is spread over the four fixtures
        assertEquals(0, PresetSteps.getStepsPhasingMillis(sequence, 0, 4));
        assertEquals(375, PresetSteps.getStepsPhasingMillis(sequence, 1, 4));
        assertEquals(1125, PresetSteps.getStepsPhasingMillis(sequence, 3, 4));
    }

    @Test
    void chaseGroupsFixturesTogether() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));
        sequence.setStepsPhasingMillis(250);
        sequence.setStepsPhasingGroupSize(2);

        assertEquals(0, PresetSteps.getStepsPhasingMillis(sequence, 0, 4));
        assertEquals(0, PresetSteps.getStepsPhasingMillis(sequence, 1, 4));
        assertEquals(250, PresetSteps.getStepsPhasingMillis(sequence, 2, 4));
    }
}
