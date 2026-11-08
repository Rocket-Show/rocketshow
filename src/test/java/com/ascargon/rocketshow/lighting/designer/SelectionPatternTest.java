package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pattern the fixtures of a preset are put in (groups, wings, every so many), like selection-pattern.spec.ts of
 * the designer (the conformance cases play the patterns on whole fixtures).
 */
class SelectionPatternTest {

    // the places of all the fixtures of a preset, as "index/count"
    private static String places(int count, double groupSize, double wings, double every) {
        List<String> places = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            SelectionPattern.Place place = SelectionPattern.getPatternPlace(index, count, groupSize, wings, every);
            places.add(place.index() + "/" + place.count());
        }

        return String.join(" ", places);
    }

    @Test
    void keepsTheOrderOfThePresetWithoutAPattern() {
        assertEquals("0/4 1/4 2/4 3/4", places(4, 1, 1, 0));
        // nothing set (0, or not a number) is no pattern either
        assertEquals("0/4 1/4 2/4 3/4", places(4, 0, Double.NaN, 0));

        // a preset read without the pattern keys plays without a pattern
        Preset preset = new Preset();
        assertEquals(new SelectionPattern.Place(2, 4), preset.getPatternPlace(2, 4));
    }

    @Test
    void putsTheFixturesOfAGroupInTheSamePlace() {
        assertEquals("0/3 0/3 1/3 1/3 2/3 2/3", places(6, 2, 1, 0));
        // a group the fixtures don't fill
        assertEquals("0/3 0/3 1/3 1/3 2/3", places(5, 2, 1, 0));
    }

    @Test
    void runsEveryOtherWingTheOtherWayRound() {
        assertEquals("0/3 1/3 2/3 2/3 1/3 0/3", places(6, 1, 2, 0));
        // an odd count: the middle one alone, the last wing ending with the last fixture
        assertEquals("0/3 1/3 2/3 1/3 0/3", places(5, 1, 2, 0));
        assertEquals("0/2 1/2 1/2 0/2 0/2 1/2", places(6, 1, 3, 0));
    }

    @Test
    void repeatsThePlacesEverySoManyFixtures() {
        assertEquals("0/2 1/2 0/2 1/2 0/2", places(5, 1, 1, 2));
        assertEquals("0/3 1/3 2/3 0/3 1/3 2/3", places(6, 1, 1, 3));
    }

    @Test
    void groupsFoldsIntoWingsAndRepeatsOneAfterTheOther() {
        // 12 fixtures in groups of 2 (6 places), in 2 wings (3 places each), every other one
        assertEquals("0/2 0/2 1/2 1/2 0/2 0/2 0/2 0/2 1/2 1/2 0/2 0/2", places(12, 2, 2, 2));

        Preset preset = new Preset();
        preset.setPatternGroupSize(2);
        preset.setPatternWings(2);
        preset.setPatternEvery(2);
        assertEquals(new SelectionPattern.Place(1, 2), preset.getPatternPlace(8, 12));
    }
}
