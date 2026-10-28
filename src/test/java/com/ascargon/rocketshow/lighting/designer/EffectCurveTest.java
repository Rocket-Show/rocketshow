package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The curves of the effects. The tempo cases mirror the specs of effect-curve in the Rocket Show
 * Designer, the shapes follow its getCurveValue.
 */
class EffectCurveTest {

    // a curve on a fixed period, so the tempo must not reach it
    private static EffectCurve timedCurve() {
        EffectCurve curve = new EffectCurve();
        curve.setLengthMillis(2000);
        curve.setPhaseMillis(500);
        return curve;
    }

    // the same curve, measured in beats instead
    private static EffectCurve beatCurve() {
        EffectCurve curve = new EffectCurve();
        curve.setLengthMode("beats");
        curve.setLengthBeats(4);
        return curve;
    }

    // a curve without phase, which starts its cycle at 0
    private static EffectCurve curve(String curveType) {
        EffectCurve curve = new EffectCurve();
        curve.setCurveType(curveType);
        curve.setLengthMillis(1000);
        return curve;
    }

    private static double valueAt(EffectCurve curve, long timeMillis) {
        return curve.getValueAtMillis(timeMillis, 0, 1, null);
    }

    @Test
    void measuresABeatByTheTempo() {
        assertEquals(500, EffectCurve.getBeatMillis(120d));
        assertEquals(1000, EffectCurve.getBeatMillis(60d));
    }

    @Test
    void fallsBackToTheDefaultTempoWithoutACompositionToTakeOneFrom() {
        double fallback = EffectCurve.getBeatMillis(EffectCurve.DEFAULT_BEATS_PER_MINUTE);

        assertEquals(fallback, EffectCurve.getBeatMillis(null));
        assertEquals(fallback, EffectCurve.getBeatMillis(0d));
    }

    @Test
    void holdsThePeriodOfACurveThatDoesNotFollowTheTempo() {
        EffectCurve curve = timedCurve();

        assertFalse(curve.isBeatSynced());
        assertEquals(2000, curve.getLengthMillis(120d));
        assertEquals(2000, curve.getLengthMillis(60d));
        assertEquals(500, curve.getPhaseMillis(60d));
    }

    @Test
    void takesThePeriodOfASyncedCurveFromTheTempo() {
        EffectCurve curve = beatCurve();

        assertTrue(curve.isBeatSynced());
        // a bar of 4 beats
        assertEquals(2000, curve.getLengthMillis(120d));
        assertEquals(4000, curve.getLengthMillis(60d));
        assertEquals(1000, curve.getLengthMillis(240d));
    }

    @Test
    void keepsASyncedCurveAtTheSamePlaceInItsPeriodWhenTheTempoChanges() {
        EffectCurve curve = beatCurve();
        curve.setPhaseBeats(1);

        // half the tempo runs the curve half as fast, so it stands at the same place in its
        // period at twice the time
        for (double cyclePosition = 0; cyclePosition < 1; cyclePosition += 0.05) {
            double fast = curve.getValueAtMillis(Math.round(cyclePosition * 2000), 0, 1, 120d);
            double slow = curve.getValueAtMillis(Math.round(cyclePosition * 4000), 0, 1, 60d);

            assertEquals(fast, slow, 1e-10);
        }
    }

    @Test
    void runsASyncedCurveForTheCyclesItIsGivenHoweverFastTheyAre() {
        EffectCurve curve = beatCurve();
        curve.setRunMode("cycles");
        curve.setRunCycles(2);

        assertEquals(4000, curve.getRunEndMillis(120d));
        assertEquals(8000, curve.getRunEndMillis(60d));
    }

    @Test
    void chasesASyncedCurveByTheBeat() {
        EffectCurve curve = beatCurve();
        curve.setPhasingMode("beats");
        curve.setPhasingBeats(1);

        assertEquals(0, curve.getPhasingMillis(0, 4, 120d));
        assertEquals(500, curve.getPhasingMillis(1, 4, 120d));
        assertEquals(1000, curve.getPhasingMillis(2, 4, 120d));
        // half the tempo makes every beat twice as long
        assertEquals(2000, curve.getPhasingMillis(2, 4, 60d));
    }

    @Test
    void chasesAgainstTheDirectionOnANegativeBeat() {
        EffectCurve curve = beatCurve();
        curve.setPhasingMode("beats");
        curve.setPhasingBeats(-0.5f);

        assertEquals(-250, curve.getPhasingMillis(1, 4, 120d));
    }

    @Test
    void spreadsAChaseOverThePeriodTheTempoGivesIt() {
        EffectCurve curve = beatCurve();
        curve.setPhasingMode("spread");
        curve.setPhasingCycles(1);

        // one full cycle distributed over four fixtures
        assertEquals(500, curve.getPhasingMillis(1, 4, 120d));
        assertEquals(1000, curve.getPhasingMillis(1, 4, 60d));
    }

    @Test
    void leavesACurveOnAFixedPeriodUntouchedByTheTempo() {
        EffectCurve curve = timedCurve();
        curve.setPhasingMode("millis");
        curve.setPhasingMillis(100);

        assertEquals(100, curve.getPhasingMillis(1, 4, 60d));
        assertEquals(curve.getValueAtMillis(700, 1, 4, 60d), curve.getValueAtMillis(700, 1, 4, 240d));
    }

    @Test
    void holdsTheFixturesOfAGroupOnTheSameStepOfTheChase() {
        EffectCurve curve = timedCurve();
        curve.setPhasingMode("millis");
        curve.setPhasingMillis(100);
        curve.setPhasingGroupSize(2);

        // two fixtures per group leaves four fixtures with two steps
        assertEquals(0, curve.getPhasingMillis(0, 4, 120d));
        assertEquals(0, curve.getPhasingMillis(1, 4, 120d));
        assertEquals(100, curve.getPhasingMillis(2, 4, 120d));
        assertEquals(100, curve.getPhasingMillis(3, 4, 120d));
    }

    @Test
    void spreadsAChaseOverTheGroupsInsteadOfTheFixtures() {
        EffectCurve curve = timedCurve();
        curve.setPhasingMode("spread");
        curve.setPhasingCycles(1);
        curve.setPhasingGroupSize(2);

        // one full cycle distributed over the two groups of four fixtures
        assertEquals(1000, curve.getPhasingMillis(2, 4, 120d));

        // a group the fixtures do not fill completely still runs as a step of its own
        assertEquals(2000 / 3d * 2, curve.getPhasingMillis(4, 5, 120d), 1e-9);
    }

    @Test
    void readsAProjectWrittenBeforeTheCurvesCouldFollowATempo() {
        Effect effect = new ObjectMapper().readValue("""
                {"type": "curve", "lengthMillis": 1000, "phaseMillis": 0, "amplitude": 1, "position": 0.5, "phasingMillis": 0, "curveType": "sine"}
                """, Effect.class);
        EffectCurve curve = assertInstanceOf(EffectCurve.class, effect);

        assertEquals("millis", curve.getLengthMode());
        assertFalse(curve.isBeatSynced());
        assertEquals(0, curve.getPhaseBeats());
        assertEquals(0, curve.getPhasingBeats());
        assertEquals(1000, curve.getLengthMillis(60d));
        assertEquals("infinite", curve.getRunMode());
        assertEquals(0.5f, curve.getDutyCycle());
    }

    @Test
    void swingsASineAroundItsPosition() {
        EffectCurve curve = curve("sine");

        // over its whole amplitude
        assertEquals(0.5, valueAt(curve, 0), 1e-9);
        assertEquals(1, valueAt(curve, 250), 1e-9);
        assertEquals(0.5, valueAt(curve, 500), 1e-9);
        assertEquals(0, valueAt(curve, 750), 1e-9);
        assertEquals(0.5, valueAt(curve, 1000), 1e-9);
    }

    @Test
    void shapesTheOtherCurves() {
        // every curve swings over its whole amplitude, like the square
        EffectCurve square = curve("square");
        assertEquals(0, valueAt(square, 100), 1e-9);
        assertEquals(1, valueAt(square, 600), 1e-9);

        EffectCurve triangle = curve("triangle");
        assertEquals(0.5, valueAt(triangle, 0), 1e-9);
        assertEquals(1, valueAt(triangle, 250), 1e-9);
        assertEquals(0, valueAt(triangle, 750), 1e-9);

        EffectCurve sawtooth = curve("sawtooth");
        assertEquals(0, valueAt(sawtooth, 0), 1e-9);
        assertEquals(0.5, valueAt(sawtooth, 500), 1e-9);

        EffectCurve reverseSawtooth = curve("reverse-sawtooth");
        assertEquals(1, valueAt(reverseSawtooth, 0), 1e-9);
        assertEquals(0.5, valueAt(reverseSawtooth, 500), 1e-9);
    }

    @Test
    void keepsTheValueBetweenNothingAndFull() {
        EffectCurve curve = curve("square");
        curve.setPosition(0.9f);
        curve.setAmplitude(2);

        assertEquals(0, valueAt(curve, 100));
        assertEquals(1, valueAt(curve, 600));
    }

    @Test
    void stretchesTheOnPartOfASquareByItsDutyCycle() {
        EffectCurve curve = curve("square");
        curve.setDutyCycle(0.1f);

        // on over the last tenth of the cycle only
        assertEquals(0, valueAt(curve, 850), 1e-9);
        assertEquals(1, valueAt(curve, 950), 1e-9);
    }

    @Test
    void keepsASineAndATriangleSymmetricAroundTheirPeakAtAnyWidth() {
        for (String curveType : List.of("sine", "triangle")) {
            for (float dutyCycle : new float[]{0.1f, 0.3f, 0.7f, 0.9f}) {
                EffectCurve curve = curve(curveType);
                curve.setDutyCycle(dutyCycle);

                // the peak stays where it is
                assertEquals(1, valueAt(curve, 250), 1e-9);

                for (int fromPeak = 10; fromPeak <= 250; fromPeak += 10) {
                    assertEquals(valueAt(curve, 250 - fromPeak), valueAt(curve, 250 + fromPeak), 1e-6);
                }
            }
        }
    }

    @Test
    void restsANarrowSineAtItsBottom() {
        EffectCurve curve = curve("sine");
        curve.setDutyCycle(0.2f);

        // a full swing over twice the width around the peak, crossing the middle at the
        // borders of the width
        assertEquals(0.5, valueAt(curve, 150), 1e-6);
        assertEquals(0.5, valueAt(curve, 350), 1e-6);

        // and at the bottom over the rest of the period
        assertEquals(0, valueAt(curve, 0), 1e-9);
        assertEquals(0, valueAt(curve, 500), 1e-9);
        assertEquals(0, valueAt(curve, 750), 1e-9);
    }

    @Test
    void restsAWideSineAtItsTop() {
        EffectCurve curve = curve("sine");
        curve.setDutyCycle(0.8f);

        assertEquals(1, valueAt(curve, 0), 1e-9);
        assertEquals(1, valueAt(curve, 500), 1e-9);
        assertEquals(0.5, valueAt(curve, 650), 1e-6);
        assertEquals(0, valueAt(curve, 750), 1e-9);
        assertEquals(0.5, valueAt(curve, 850), 1e-6);
    }

    @Test
    void restsACurveFlatBeyondTheMiddle() {
        EffectCurve curve = curve("sine");

        curve.setPosition(-0.5f);
        for (int millis = 0; millis < 1000; millis += 50) {
            assertEquals(0, valueAt(curve, millis), 1e-9);
        }

        curve.setPosition(1.5f);
        for (int millis = 0; millis < 1000; millis += 50) {
            assertEquals(1, valueAt(curve, millis), 1e-9);
        }
    }

    @Test
    void holdsTheLastValueAfterItsRun() {
        EffectCurve curve = curve("sawtooth");
        curve.setRunMode("cycles");
        curve.setRunCycles(2);

        // the run ends on a cycle border: the value just before it stays
        assertEquals(1, valueAt(curve, 5000), 1e-6);

        curve.setEndMode("base");
        assertNull(curve.getValueAtMillis(5000, 0, 1, null));
    }

    @Test
    void startsTheRunOfAChasedFixtureLater() {
        EffectCurve curve = curve("sawtooth");
        curve.setRunMode("duration");
        curve.setRunDurationMillis(1000);
        curve.setPhasingMillis(300);

        assertNull(curve.getValueAtMillis(200, 1, 2, null));
        assertEquals(0, curve.getValueAtMillis(300, 1, 2, null), 1e-9);
    }

}
