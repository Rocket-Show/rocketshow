package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The values of a step a preset plays: all of them but the ones turned off in the preset editor of the designer
 * (deactivated), which the step keeps to turn them on again, e.g. after a venue they didn't suit. The same list if
 * none of them is off, as every frame reads them. The designer plays them the very same way (played-values.ts).
 *
 * @author Moritz A. Vieli
 */
final class PlayedValues {

    private PlayedValues() {
    }

    static List<FixtureCapabilityValue> ofCapabilities(List<FixtureCapabilityValue> values) {
        return of(values, value -> Boolean.TRUE.equals(value.getDeactivated()));
    }

    static List<FixtureChannelValue> ofChannels(List<FixtureChannelValue> values) {
        return of(values, value -> Boolean.TRUE.equals(value.getDeactivated()));
    }

    private static <T> List<T> of(List<T> values, Predicate<T> deactivated) {
        if (values == null || values.stream().noneMatch(deactivated)) {
            return values;
        }

        return values.stream().filter(deactivated.negate()).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
}
