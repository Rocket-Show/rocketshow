package com.ascargon.rocketshow.lighting.designer;

import java.util.List;
import java.util.Objects;

/**
 * Works out which values a preset applies at a point in time: the values of the step it
 * has reached, or the ones interpolated between two steps while a transition runs.
 * <p>
 * This mirrors PresetStepService of the Rocket Show Designer, which the designer's own
 * preview runs. Both have to stay in step, or a show looks different on stage than it
 * did while it was written.
 *
 * @author Moritz A. Vieli
 */
public final class PresetSteps {

    private PresetSteps() {
    }

    // the length of one pass through the steps. Without a length of its own, the last
    // step holds as long as the one before it lasted, which loops an evenly spaced
    // chase the way it is written down.
    public static double getStepsLoopMillis(Preset preset) {
        if (preset.getStepsLoopMillis() != null && preset.getStepsLoopMillis() > 0) {
            return preset.getStepsLoopMillis();
        }

        List<PresetStep> steps = preset.getSteps();

        if (steps == null || steps.size() < 2) {
            return 0;
        }

        PresetStep last = steps.get(steps.size() - 1);
        PresetStep previous = steps.get(steps.size() - 2);

        return last.getStartMillis() - steps.get(0).getStartMillis() + (last.getStartMillis() - previous.getStartMillis());
    }

    // whether the sequence starts at a different point for every fixture of the preset,
    // which makes the steps chase over them
    public static boolean stepsArePhased(Preset preset) {
        if (preset.getSteps() == null || preset.getSteps().size() < 2) {
            return false;
        }

        if ("spread".equals(preset.getStepsPhasingMode())) {
            return preset.getStepsPhasingCycles() != 0;
        }

        return preset.getStepsPhasingMillis() != 0;
    }

    // the time the sequence is shifted by for the passed fixture of the preset. This
    // follows the same rules the effect curves chase by.
    public static double getStepsPhasingMillis(Preset preset, Integer fixtureIndex, Integer fixtureCount) {
        // a number of fixtures: the designer rounds it
        long groupSize = Math.max(Math.round(preset.getStepsPhasingGroupSize()), 1);
        double step = Math.floor((double) (fixtureIndex == null ? 0 : fixtureIndex) / groupSize);

        if ("spread".equals(preset.getStepsPhasingMode())) {
            // distribute the passes over all chase steps of the preset
            int count = fixtureCount == null || fixtureCount == 0 ? 1 : fixtureCount;
            double steps = Math.max(Math.ceil((double) count / groupSize), 1);
            return step / steps * preset.getStepsPhasingCycles() * PresetStepsOrder.getPassMillis(preset);
        }

        return step * preset.getStepsPhasingMillis();
    }

    // When the step at the passed index gives way to the next one. The last step holds
    // for as long as a pass of the sequence would have given it, which is what a looping
    // preset starts over after and what one which does not loop stands still on.
    public static double getStepEndMillis(Preset preset, int index) {
        List<PresetStep> steps = preset.getSteps();

        if (index < steps.size() - 1) {
            return steps.get(index + 1).getStartMillis();
        }

        return steps.get(0).getStartMillis() + getStepsLoopMillis(preset);
    }

    // Where the transition into a step ends: it begins as the step does and travels from
    // the step before it over the whole time this step lasts, unless the step carries a
    // shorter time of its own. A transition never runs past the step which follows it.
    public static double getTransitionEndMillis(PresetStep step, double startMillis, double endMillis) {
        if (endMillis <= startMillis) {
            return startMillis;
        }

        if (step.getTransitionMillis() == null) {
            return endMillis;
        }

        return Math.min(startMillis + step.getTransitionMillis(), endMillis);
    }

    // the values of a single step, without copying them: the state is only read from
    public static PresetStepState getStepState(PresetStep step) {
        PresetStepState state = new PresetStepState();

        if (step == null) {
            return state;
        }

        state.setCurrentStep(step);

        if (step.getFixtureChannelValues() != null) {
            state.setFixtureChannelValues(step.getFixtureChannelValues());
        }
        if (step.getFixtureCapabilityValues() != null) {
            state.setFixtureCapabilityValues(step.getFixtureCapabilityValues());
        }
        if (step.getEffectAmounts() != null) {
            for (PresetStepEffectAmount effectAmount : step.getEffectAmounts()) {
                state.getEffectAmounts().put(effectAmount.getEffectUuid(), effectAmount.getAmount());
            }
        }

        return state;
    }

    // the values the preset applies at the passed time, relative to its own start
    public static PresetStepState getStateAtMillis(Preset preset, double presetTimeMillis) {
        List<PresetStep> steps = preset.getSteps();

        if (steps == null || steps.isEmpty()) {
            // an old project keeps its values on the preset itself
            PresetStepState state = new PresetStepState();

            if (preset.getFixtureChannelValues() != null) {
                state.setFixtureChannelValues(preset.getFixtureChannelValues());
            }
            if (preset.getFixtureCapabilityValues() != null) {
                state.setFixtureCapabilityValues(preset.getFixtureCapabilityValues());
            }

            return state;
        }

        if (steps.size() == 1) {
            return getStepState(steps.get(0));
        }

        if (PresetStepsOrder.isOrdered(preset)) {
            return getOrderedStateAtMillis(preset, presetTimeMillis);
        }

        PresetStep first = steps.get(0);
        double timeMillis = presetTimeMillis;
        double loopMillis = 0;

        if (preset.isStepsLoop()) {
            loopMillis = getStepsLoopMillis(preset);

            if (loopMillis > 0) {
                // fold the time into a single pass, starting over at the first step
                double offset = timeMillis - first.getStartMillis();
                timeMillis = first.getStartMillis() + ((offset % loopMillis) + loopMillis) % loopMillis;
            }
        }

        // the step started last
        int index = -1;

        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStartMillis() > timeMillis) {
                break;
            }

            index = i;
        }

        if (index < 0) {
            // before the first step: it is what the preset starts out with
            return getStepState(first);
        }

        PresetStep current = steps.get(index);
        double endMillis = getStepEndMillis(preset, index);

        // The step travelled from, which is the last one again once the sequence starts
        // over. The first step of a preset which does not loop has nothing to travel
        // from, so it is simply what the preset opens with.
        PresetStep previous;

        if (index > 0) {
            previous = steps.get(index - 1);
        } else if (loopMillis > 0) {
            previous = steps.get(steps.size() - 1);
        } else {
            previous = null;
        }

        double transitionEndMillis = getTransitionEndMillis(current, current.getStartMillis(), endMillis);

        if (previous == null || timeMillis >= transitionEndMillis) {
            return getStepState(current);
        }

        double position = TransitionCurve.apply(current.getTransitionCurve(), (timeMillis - current.getStartMillis()) / (transitionEndMillis - current.getStartMillis()));

        return interpolate(previous, current, position);
    }

    // The values a preset playing its steps in another order than they are written down in applies at the passed
    // time, relative to its own start: each step lasts as long as it does written down, and travels from the step
    // played before it, over the time of its own transition.
    private static PresetStepState getOrderedStateAtMillis(Preset preset, double presetTimeMillis) {
        List<PresetStep> steps = preset.getSteps();
        double[] lengths = PresetStepsOrder.getStepLengths(preset);
        double passMillis = PresetStepsOrder.getPassMillis(preset);
        boolean loops = preset.isStepsLoop() && passMillis > 0;
        double offsetMillis = presetTimeMillis - steps.get(0).getStartMillis();
        double pass = 0;

        if (loops) {
            // fold the time into the pass it is in
            pass = Math.floor(offsetMillis / passMillis);
            offsetMillis = Math.max(offsetMillis - pass * passMillis, 0);
        }

        List<Integer> order = PresetStepsOrder.getOrderIndices(preset, pass);

        if (order.isEmpty()) {
            // none of the steps lasts any time
            return getStepState(steps.get(0));
        }

        if (offsetMillis < 0) {
            // before the sequence starts: the step it opens with is what the preset starts out with
            return getStepState(steps.get(order.get(0)));
        }

        // the step played at that time, and where it starts in the pass. The last one holds, if the sequence doesn't
        // loop.
        int position = 0;
        double startMillis = 0;

        while (position < order.size() - 1 && startMillis + lengths[order.get(position)] <= offsetMillis) {
            startMillis += lengths[order.get(position)];
            position++;
        }

        PresetStep current = steps.get(order.get(position));
        double endMillis = startMillis + lengths[order.get(position)];

        // The step travelled from: the one played before it, which is the last one of the pass before once the
        // sequence starts over. The first step of a sequence which does not loop has nothing to travel from.
        PresetStep previous = null;

        if (position > 0) {
            previous = steps.get(order.get(position - 1));
        } else if (loops) {
            List<Integer> previousOrder = PresetStepsOrder.getOrderIndices(preset, pass - 1);
            previous = steps.get(previousOrder.get(previousOrder.size() - 1));
        }

        double transitionEndMillis = getTransitionEndMillis(current, startMillis, endMillis);

        if (previous == null || offsetMillis >= transitionEndMillis) {
            return getStepState(current);
        }

        double curvePosition = TransitionCurve.apply(current.getTransitionCurve(), (offsetMillis - startMillis) / (transitionEndMillis - startMillis));

        return interpolate(previous, current, curvePosition);
    }

    private static boolean capabilityValuesMatch(FixtureCapabilityValue value1, FixtureCapabilityValue value2) {
        // a capability value is identified by what it drives, not by the value it holds
        return value1.getType() == value2.getType()
                && value1.getColor() == value2.getColor()
                && Objects.equals(value1.getWheel(), value2.getWheel())
                && Objects.equals(value1.getProfileUuid(), value2.getProfileUuid());
    }

    private static FixtureCapabilityValue getMatchingCapabilityValue(List<FixtureCapabilityValue> values, FixtureCapabilityValue value) {
        for (FixtureCapabilityValue candidate : values) {
            if (capabilityValuesMatch(candidate, value)) {
                return candidate;
            }
        }

        return null;
    }

    private static FixtureChannelValue getMatchingChannelValue(List<FixtureChannelValue> values, FixtureChannelValue value) {
        for (FixtureChannelValue candidate : values) {
            if (Objects.equals(candidate.getChannelName(), value.getChannelName()) && Objects.equals(candidate.getProfileUuid(), value.getProfileUuid())) {
                return candidate;
            }
        }

        return null;
    }

    private static PresetStepState interpolate(PresetStep from, PresetStep to, double position) {
        PresetStepState state = new PresetStepState();

        // the preset is on the step it is travelling into from the moment that step starts
        state.setCurrentStep(to);

        // A value only one of the two steps carries is held as it is: a step not naming
        // a channel means it does not drive that channel, not that it drives it to zero.
        for (FixtureChannelValue toValue : to.getFixtureChannelValues()) {
            FixtureChannelValue fromValue = getMatchingChannelValue(from.getFixtureChannelValues(), toValue);

            if (fromValue == null) {
                state.getFixtureChannelValues().add(toValue);
                continue;
            }

            FixtureChannelValue value = new FixtureChannelValue();
            value.setChannelName(toValue.getChannelName());
            value.setProfileUuid(toValue.getProfileUuid());
            value.setValue(fromValue.getValue() + (toValue.getValue() - fromValue.getValue()) * position);
            state.getFixtureChannelValues().add(value);
        }

        for (FixtureChannelValue fromValue : from.getFixtureChannelValues()) {
            if (getMatchingChannelValue(to.getFixtureChannelValues(), fromValue) == null) {
                state.getFixtureChannelValues().add(fromValue);
            }
        }

        for (FixtureCapabilityValue toValue : to.getFixtureCapabilityValues()) {
            FixtureCapabilityValue fromValue = getMatchingCapabilityValue(from.getFixtureCapabilityValues(), toValue);

            if (fromValue == null) {
                state.getFixtureCapabilityValues().add(toValue);
                continue;
            }

            if (toValue.getSlotNumber() != null || fromValue.getSlotNumber() != null
                    || toValue.getValuePercentage() == null || fromValue.getValuePercentage() == null) {
                // a wheel cannot stand between two of its slots: it turns as soon as the
                // transition starts, which a snap curve holds back to the end of it
                state.getFixtureCapabilityValues().add(position > 0 ? toValue : fromValue);
                continue;
            }

            FixtureCapabilityValue value = new FixtureCapabilityValue();
            value.setType(toValue.getType());
            value.setColor(toValue.getColor());
            value.setWheel(toValue.getWheel());
            value.setProfileUuid(toValue.getProfileUuid());
            value.setSlotNumber(toValue.getSlotNumber());
            value.setValuePercentage(fromValue.getValuePercentage() + (toValue.getValuePercentage() - fromValue.getValuePercentage()) * position);
            state.getFixtureCapabilityValues().add(value);
        }

        for (FixtureCapabilityValue fromValue : from.getFixtureCapabilityValues()) {
            if (getMatchingCapabilityValue(to.getFixtureCapabilityValues(), fromValue) == null) {
                state.getFixtureCapabilityValues().add(fromValue);
            }
        }

        for (PresetStepEffectAmount effectAmount : to.getEffectAmounts()) {
            double fromAmount = from.getEffectAmount(effectAmount.getEffectUuid());
            state.getEffectAmounts().put(effectAmount.getEffectUuid(), fromAmount + (effectAmount.getAmount() - fromAmount) * position);
        }

        for (PresetStepEffectAmount effectAmount : from.getEffectAmounts()) {
            if (!state.getEffectAmounts().containsKey(effectAmount.getEffectUuid())) {
                // the step travelled to says nothing about this effect, so it opens fully
                state.getEffectAmounts().put(effectAmount.getEffectUuid(), effectAmount.getAmount() + (1 - effectAmount.getAmount()) * position);
            }
        }

        return state;
    }
}
