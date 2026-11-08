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
        step.setTransitionMillis((double) transitionMillis);

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
        sequence.setStepsLoopMillis(2000d);

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

    private FixtureCapabilityValue capability(FixtureCapability.FixtureCapabilityType type, String blade, Double valuePercentage, Double valueDegrees, Double spreadDegrees) {
        FixtureCapabilityValue capabilityValue = new FixtureCapabilityValue();
        capabilityValue.setType(type);
        capabilityValue.setBlade(blade);
        capabilityValue.setValuePercentage(valuePercentage);
        capabilityValue.setValueDegrees(valueDegrees);
        capabilityValue.setSpreadDegrees(spreadDegrees);

        return capabilityValue;
    }

    private FixtureCapabilityValue findCapability(PresetStepState state, FixtureCapability.FixtureCapabilityType type, String blade) {
        return state.getFixtureCapabilityValues().stream()
                .filter(value -> value.getType() == type && java.util.Objects.equals(value.getBlade(), blade))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void eachMovingHeadTravelsOnItsOwnFromOneFanToTheNext() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);
        from.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.Pan, null, null, 0d, null));
        from.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.Tilt, null, 0.2, null, null));
        to.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.Pan, null, null, 20d, 120d));
        to.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.Tilt, null, 0.6, null, null));

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 1250);
        FixtureCapabilityValue pan = findCapability(state, FixtureCapability.FixtureCapabilityType.Pan, null);
        FixtureCapabilityValue tilt = findCapability(state, FixtureCapability.FixtureCapabilityType.Tilt, null);

        // where the heads point is worked out for each of them, from the value they travel from (see PanTilt)
        assertEquals(20, pan.getValueDegrees());
        assertEquals(120, pan.getSpreadDegrees());
        assertEquals(from.getFixtureCapabilityValues().get(0), pan.getTransitionFrom());
        assertEquals(0.25, pan.getTransitionPosition());

        // two percentages of the range alone travel like any other percentage
        assertEquals(0.3, tilt.getValuePercentage(), 1e-10);
        assertEquals(null, tilt.getTransitionFrom());

        // a step on its own travels from nothing, and the values of a step are never touched
        assertEquals(null, PresetSteps.getStateAtMillis(preset(from, to), 2000).getFixtureCapabilityValues().get(0).getTransitionFrom());
        assertEquals(null, to.getFixtureCapabilityValues().get(0).getTransitionFrom());
    }

    @Test
    void eachBladeOfAFramingShutterTravelsFromTheSameBlade() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);
        from.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.BladeInsertion, "Top", 0d, null, null));
        from.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.BladeInsertion, "Bottom", 1d, null, null));
        to.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.BladeInsertion, "Bottom", 0.5, null, null));
        to.getFixtureCapabilityValues().add(capability(FixtureCapability.FixtureCapabilityType.BladeInsertion, "Top", 1d, null, null));

        PresetStepState state = PresetSteps.getStateAtMillis(preset(from, to), 1500);

        // each keeps its blade, which says which blade of the shutter it moves
        assertEquals(2, state.getFixtureCapabilityValues().size());
        assertEquals(0.5, findCapability(state, FixtureCapability.FixtureCapabilityType.BladeInsertion, "Top").getValuePercentage(), 1e-10);
        assertEquals(0.75, findCapability(state, FixtureCapability.FixtureCapabilityType.BladeInsertion, "Bottom").getValuePercentage(), 1e-10);
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
        capabilityValue.setSlotNumber((double) slotNumber);

        return capabilityValue;
    }

    @Test
    void wheelTurnsAsSoonAsTheTransitionStarts() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);

        from.getFixtureCapabilityValues().add(wheelSlot(1));
        to.getFixtureCapabilityValues().add(wheelSlot(4));

        Preset sequence = preset(from, to);

        assertEquals(1, PresetSteps.getStateAtMillis(sequence, 1000).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, PresetSteps.getStateAtMillis(sequence, 1500).getFixtureCapabilityValues().get(0).getSlotNumber());
    }

    @Test
    void wheelIsHeldUntilTheEndOfASnappingTransition() {
        PresetStep from = step(0, 0, 0);
        PresetStep to = step(1000, 0, 1000);
        to.setTransitionCurve("snap");

        from.getFixtureCapabilityValues().add(wheelSlot(1));
        to.getFixtureCapabilityValues().add(wheelSlot(4));

        Preset sequence = preset(from, to);

        assertEquals(1, PresetSteps.getStateAtMillis(sequence, 1500).getFixtureCapabilityValues().get(0).getSlotNumber());
        assertEquals(4, PresetSteps.getStateAtMillis(sequence, 2000).getFixtureCapabilityValues().get(0).getSlotNumber());
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

    // three steps a second apart: 10, 20 and 30, each jumping in (no transition)
    private Preset threeSteps(String order, boolean loop) {
        Preset sequence = preset(step(0, 10, 0), step(1000, 20, 0), step(2000, 30, 0));
        sequence.setStepsOrder(order);
        sequence.setStepsLoop(loop);

        return sequence;
    }

    private List<Double> played(Preset sequence, int count) {
        List<Double> values = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            values.add(dimmerAt(sequence, i * 1000L + 500));
        }

        return values;
    }

    @Test
    void playsTheStepsBackwardHoldingTheFirstOneAtTheEnd() {
        assertEquals(List.of(30.0, 20.0, 10.0, 10.0, 10.0), played(threeSteps("backward", false), 5));
        assertEquals(List.of(30.0, 20.0, 10.0, 30.0, 20.0, 10.0), played(threeSteps("backward", true), 6));
    }

    @Test
    void playsTheStepsForwardAndBackWithoutPlayingTheEndsTwiceInARow() {
        assertEquals(List.of(10.0, 20.0, 30.0, 20.0, 10.0, 20.0, 30.0, 20.0, 10.0), played(threeSteps("pingpong", true), 9));
        assertEquals(List.of(10.0, 20.0, 30.0, 20.0, 10.0, 10.0, 10.0), played(threeSteps("pingpong", false), 7));
        assertEquals(4000, PresetStepsOrder.getPassMillis(threeSteps("pingpong", true)));
        assertEquals(5000, PresetStepsOrder.getPassMillis(threeSteps("pingpong", false)));
    }

    @Test
    void playsEveryStepOncePerPassInARandomOrderNeverOneTwiceInARow() {
        Preset sequence = preset(step(0, 10, 0), step(1000, 20, 0), step(2000, 30, 0), step(3000, 40, 0), step(4000, 50, 0));
        sequence.setStepsOrder("random");
        sequence.setStepsLoop(true);
        List<Double> values = played(sequence, 5 * 20);

        for (int pass = 0; pass < 20; pass++) {
            List<Double> passValues = new ArrayList<>(values.subList(pass * 5, pass * 5 + 5));
            passValues.sort(Double::compare);
            assertEquals(List.of(10.0, 20.0, 30.0, 40.0, 50.0), passValues);
        }
        for (int i = 1; i < values.size(); i++) {
            assertTrue(!values.get(i).equals(values.get(i - 1)));
        }

        sequence.setStepsSeed(2);
        assertFalse(played(sequence, 5 * 20).equals(values));
    }

    @Test
    void travelsIntoAStepFromTheOnePlayedBeforeIt() {
        Preset sequence = preset(step(0, 10, 0), step(1000, 20, 1000), step(2000, 30, 1000));
        sequence.setStepsOrder("backward");
        sequence.setStepsLoop(true);

        // 30 opens the pass, travelling from 10, which ended the pass before
        assertEquals(20, dimmerAt(sequence, 3500));
        // 20 travels from 30 over its second
        assertEquals(25, dimmerAt(sequence, 1500));
    }

    @Test
    void playsTheStepsOfAnOrderItDoesNotKnowForward() {
        assertEquals(List.of(10.0, 20.0, 30.0, 10.0), played(threeSteps("sideways", true), 4));
    }
}
