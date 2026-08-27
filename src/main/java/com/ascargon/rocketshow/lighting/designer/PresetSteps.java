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
    public static long getStepsLoopMillis(Preset preset) {
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
        int groupSize = Math.max(preset.getStepsPhasingGroupSize(), 1);
        int step = (fixtureIndex == null ? 0 : fixtureIndex) / groupSize;

        if ("spread".equals(preset.getStepsPhasingMode())) {
            // distribute the passes over all chase steps of the preset
            int count = fixtureCount == null ? 1 : fixtureCount;
            int steps = Math.max((int) Math.ceil((double) count / groupSize), 1);
            return (double) step / steps * preset.getStepsPhasingCycles() * getStepsLoopMillis(preset);
        }

        return (double) step * preset.getStepsPhasingMillis();
    }

    // the values of a single step, without copying them: the state is only read from
    public static PresetStepState getStepState(PresetStep step) {
        PresetStepState state = new PresetStepState();

        if (step == null) {
            return state;
        }

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

    // the values the preset applies at the passed time, relative to its own start.
    // forceLoop runs the sequence over and over even when the preset does not loop,
    // which is what the designer watches while it edits the steps.
    public static PresetStepState getStateAtMillis(Preset preset, long presetTimeMillis, boolean forceLoop) {
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

        PresetStep first = steps.get(0);
        double timeMillis = presetTimeMillis;
        long loopMillis = 0;

        if (preset.isStepsLoop() || forceLoop) {
            loopMillis = getStepsLoopMillis(preset);

            if (loopMillis > 0) {
                // fold the time into a single pass, starting over at the first step
                double offset = timeMillis - first.getStartMillis();
                timeMillis = first.getStartMillis() + ((offset % loopMillis) + loopMillis) % loopMillis;
            }
        }

        // the step reached last
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

        // the step being travelled to, which is the first one again once the sequence loops
        PresetStep target;
        double targetStartMillis;

        if (index < steps.size() - 1) {
            target = steps.get(index + 1);
            targetStartMillis = target.getStartMillis();
        } else if (loopMillis > 0) {
            target = first;
            targetStartMillis = (double) first.getStartMillis() + loopMillis;
        } else {
            // the last step is held until the preset ends
            return getStepState(current);
        }

        // the transition never reaches back past the step it starts from
        double transitionStartMillis = Math.max(targetStartMillis - target.getTransitionMillis(), current.getStartMillis());

        if (timeMillis <= transitionStartMillis || targetStartMillis <= transitionStartMillis) {
            return getStepState(current);
        }

        double position = TransitionCurve.apply(target.getTransitionCurve(), (timeMillis - transitionStartMillis) / (targetStartMillis - transitionStartMillis));

        return interpolate(current, target, position);
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
