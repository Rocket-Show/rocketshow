package com.ascargon.rocketshow.lighting.designer;

/**
 * The pattern the fixtures of a preset are put in for its chases and for what it fans out over them in its own order
 * (see Preset.patternGroupSize, patternWings and patternEvery): the place of a fixture in the order of the preset,
 * grouped (the fixtures of a group take the same place), folded into wings (every other wing the other way round, so a
 * chase runs out from the middle or in from both ends) and repeated every so many fixtures (every other fixture in one
 * color, the others in another one). The designer places them the very same way (selection-pattern.ts).
 */
final class SelectionPattern {

    /**
     * Where a fixture is in the pattern: its index and the count of the places of the pattern.
     */
    record Place(int index, int count) {
    }

    private SelectionPattern() {
    }

    // a number of a pattern, 0 (and nothing) being the passed one, like the designer reads it (number || fallback)
    private static long rounded(double value, double fallback) {
        return Math.round(value == 0 || Double.isNaN(value) ? fallback : value);
    }

    /**
     * Where a fixture at the passed place in the order of its preset (of so many) is in the pattern. Without a pattern,
     * where it is in the order of the preset.
     */
    static Place getPatternPlace(int index, int count, double groupSize, double wings, double every) {
        int patternIndex = index;
        int patternCount = count;

        long size = Math.max(rounded(groupSize, 1), 1);

        if (size > 1) {
            patternIndex = (int) Math.floor((double) patternIndex / size);
            patternCount = (int) Math.ceil((double) patternCount / size);
        }

        long wingCount = Math.max(rounded(wings, 1), 1);

        if (wingCount > 1 && patternCount > 1) {
            int wingSize = (int) Math.ceil((double) patternCount / wingCount);
            int wing = (int) Math.floor((double) patternIndex / wingSize);

            if (wing % 2 == 1) {
                // the other way round, from the end of the wing (the last wing ends with the last fixture)
                int wingEnd = Math.min((wing + 1) * wingSize, patternCount) - 1;
                patternIndex = wingEnd - patternIndex;
            } else {
                patternIndex = patternIndex - wing * wingSize;
            }

            patternCount = wingSize;
        }

        long repeat = rounded(every, 0);

        if (repeat > 1 && patternCount > 1) {
            patternIndex = (int) (patternIndex % repeat);
            patternCount = (int) Math.min(repeat, patternCount);
        }

        return new Place(patternIndex, patternCount);
    }
}
