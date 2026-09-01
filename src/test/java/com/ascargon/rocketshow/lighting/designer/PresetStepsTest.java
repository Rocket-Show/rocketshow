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

    // a step driving one channel to the passed value, starting at startMillis. Without a
    // transition of its own a step travels over the whole time it lasts, so the cases
    // which want a jump ask for one.
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
        PresetStepState state = PresetSteps.getStateAtMillis(preset, timeMillis);

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
    void firstStepIsHeldBeforeItStarts() {
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
    void stepTravelsIntoItsValuesOverTheTimeItStartsWith() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 400));

        // the transition runs over the 400 ms the second step opens with
        assertEquals(100, dimmerAt(sequence, 999));
        assertEquals(100, dimmerAt(sequence, 1000));
        assertEquals(150, dimmerAt(sequence, 1200));
        assertEquals(200, dimmerAt(sequence, 1400));
        assertEquals(200, dimmerAt(sequence, 1800));
    }

    @Test
    void stepWhichChangesNothingStandsStill() {
        // three steps of black, black and white: the white belongs to the third step, so
        // nothing may move while the second one plays
        Preset sequence = preset(step(0, 0, 0), step(1000, 0, 0), step(2000, 255, 0));
        sequence.getSteps().get(2).setTransitionMillis(null);

        assertEquals(0, dimmerAt(sequence, 1000));
        assertEquals(0, dimmerAt(sequence, 1999));
        assertEquals(127.5, dimmerAt(sequence, 2500));
        assertEquals(255, dimmerAt(sequence, 3000));
    }

    @Test
    void transitionDoesNotRunPastTheStepWhichFollowsIt() {
        Preset sequence = preset(step(0, 100, 0), step(500, 200, 5000), step(1000, 300, 0));

        // the second step would travel into the third one, which it cannot
        assertEquals(100, dimmerAt(sequence, 500));
        assertEquals(150, dimmerAt(sequence, 750));
        assertEquals(300, dimmerAt(sequence, 1000));
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
        Preset sequence = preset(step(0, 100, 500), step(1000, 300, 0));
        sequence.setStepsLoop(true);
        sequence.setStepsLoopMillis(2000L);

        // the wrap runs over the 500 ms the first step of the next pass opens with
        assertEquals(300, dimmerAt(sequence, 2000));
        assertEquals(200, dimmerAt(sequence, 2250));
        assertEquals(100, dimmerAt(sequence, 2500));
    }

    @Test
    void stepWithoutATransitionOfItsOwnTravelsOverItsWholeLength() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));
        sequence.getSteps().get(1).setTransitionMillis(null);

        // the last step is held for the 1000 ms a pass would have given it and travels
        // into its value over all of them
        assertEquals(100, dimmerAt(sequence, 1000));
        assertEquals(150, dimmerAt(sequence, 1500));
        assertEquals(200, dimmerAt(sequence, 2000));
    }

    @Test
    void stepWhoseTransitionWasSetToNothingJumps() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 0));

        assertEquals(100, dimmerAt(sequence, 999));
        assertEquals(200, dimmerAt(sequence, 1000));
    }

    @Test
    void stateNamesTheStepItIsOn() {
        Preset sequence = preset(step(0, 100, 0), step(1000, 200, 400));

        assertEquals(sequence.getSteps().get(0), PresetSteps.getStateAtMillis(sequence, 500).getCurrentStep());
        // it is on the second step from the moment that one starts, transition and all
        assertEquals(sequence.getSteps().get(1), PresetSteps.getStateAtMillis(sequence, 1000).getCurrentStep());
        assertEquals(sequence.getSteps().get(1), PresetSteps.getStateAtMillis(sequence, 1200).getCurrentStep());
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

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 1500);

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

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 1500);

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

        assertEquals(1, (int) PresetSteps.getStateAtMillis(sequence, 1000).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, (int) PresetSteps.getStateAtMillis(sequence, 1500).getFixtureCapabilityValues().get(0).getSlotNumber());
    }

    @Test
    void wheelIsHeldUntilTheEndOfASnappingTransition() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);
        to.setTransitionCurve("snap");

        from.getFixtureCapabilityValues().add(wheelSlot(1));
        to.getFixtureCapabilityValues().add(wheelSlot(4));

        Preset sequence = preset(from, to);

        assertEquals(1, (int) PresetSteps.getStateAtMillis(sequence, 1500).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, (int) PresetSteps.getStateAtMillis(sequence, 2000).getFixtureCapabilityValues().get(0).getSlotNumber());
    }

    @Test
    void transitionIsShapedByItsCurve() {
        Preset sequence = preset(step(0, 0, 0), step(1000, 100, 1000));
        sequence.getSteps().get(1).setTransitionCurve("ease-in");

        // half way through an ease-in, a quarter of the distance is covered
        assertEquals(25, dimmerAt(sequence, 1500), 1e-10);
    }

    @Test
    void effectRunsFullyUnlessAStepSaysOtherwise() {
        Preset sequence = preset(step(0, 0, 0), step(1000, 100, 0));

        assertEquals(1, PresetSteps.getStateAtMillis(sequence, 0).getEffectAmount("effect"));
    }

    @Test
    void effectAmountIsInterpolated() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);

        from.getEffectAmounts().add(effectAmount(0));
        to.getEffectAmounts().add(effectAmount(1));

        assertEquals(0.5, PresetSteps.getStateAtMillis(preset(from, to), 1500).getEffectAmount("effect"), 1e-10);
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
