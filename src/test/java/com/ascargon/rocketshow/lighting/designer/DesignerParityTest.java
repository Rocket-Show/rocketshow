package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rocket Show reads a project like the designer does, where the two could differ: numbers which are null, missing, not
 * whole or written as text, names it doesn't know and the pixel groups of the fixture library. What it reads is what
 * it hands to the lighting engine (see DesignerProjectJson). The specs of the designer check the same cases (see
 * "parity" in the specs of the designer).
 */
class DesignerParityTest {

    private static <T> T read(String json, Class<T> type) {
        return DesignerJson.MAPPER.readValue(json, type);
    }

    @Test
    void keepsTheDefaultOfAValueWhichIsNull() {
        Scene scene = read("{\"dimmer\": null, \"fadeInMillis\": null, \"fadeOutMillis\": null}", Scene.class);
        assertEquals(1, scene.getDimmer());
        assertEquals(0, scene.getFadeInMillis());
        assertEquals(0, scene.getFadeOutMillis());

        EffectCurve curve = read("{\"type\": \"curve\", \"lengthMillis\": null, \"amplitude\": null, \"position\": null, \"curveType\": null}", EffectCurve.class);
        assertEquals(2500, curve.getLengthMillis());
        assertEquals(1, curve.getAmplitude());
        assertEquals(0.5, curve.getPosition());
        assertEquals("sine", curve.getCurveType());
        assertTrue(curve.isVisible());

        Project project = read("{\"masterDimmerValue\": null}", Project.class);
        assertEquals(1, project.getMasterDimmerValue());
    }

    // earlier designers saved a few of the values typed in as text
    @Test
    void readsANumberSavedAsTextAsTheNumberItIs() {
        assertEquals(200, read("{\"value\": \"200\"}", FixtureChannelValue.class).getValue());
        assertEquals(12, read("{\"value\": \" 12 \"}", FixtureChannelValue.class).getValue());
        assertEquals(100, read("{\"value\": \"1e2\"}", FixtureChannelValue.class).getValue());
        assertNull(read("{\"value\": \"\"}", FixtureChannelValue.class).getValue());
        assertEquals(127.5, read("{\"value\": 127.5}", FixtureChannelValue.class).getValue());

        assertEquals(128, read("{\"beatsPerMinute\": \"128\"}", Composition.class).getBeatsPerMinute());
    }

    @Test
    void startsASceneWithoutFades() {
        Scene scene = read("{}", Scene.class);
        assertEquals(0, scene.getFadeInMillis());
        assertEquals(0, scene.getFadeOutMillis());
    }

    @Test
    void readsANameItDoesNotKnowAsMissing() {
        FixtureCapability capability = read("{\"type\": \"SomethingNew\", \"color\": \"Ultraviolet\"}", FixtureCapability.class);
        assertNull(capability.getType());
        assertNull(capability.getColor());
    }

    @Test
    void keepsTheFractionsOfTheNumbers() {
        EffectCurve curve = read("{\"type\": \"curve\", \"lengthMillis\": 1000.5, \"amplitude\": 0.1, \"dutyCycle\": 0.3, \"phasingMillis\": 0.25}", EffectCurve.class);
        assertEquals(1000.5, curve.getLengthMillis());
        // as a double, not a float
        assertEquals(0.1, curve.getAmplitude());
        assertEquals(0.3, curve.getDutyCycle());
        assertEquals(0.25, curve.getPhasingMillis());

        Preset preset = read("{\"fadeInMillis\": 1000.5, \"startMillis\": 250.25, \"stepsPhasingCycles\": 0.1}", Preset.class);
        assertEquals(1000.5, preset.getFadeInMillis());
        assertEquals(250.25, preset.getStartMillis());
        assertEquals(0.1, preset.getStepsPhasingCycles());

        ScenePlaybackRegion region = read("{\"startMillis\": 1234.7, \"endMillis\": 6857.142857}", ScenePlaybackRegion.class);
        assertEquals(1234.7, region.getStartMillis());
        assertEquals(6857.142857, region.getEndMillis());
    }

    private static FixtureChannel channel(String json) {
        return read(json, FixtureChannel.class);
    }

    private static List<FixtureMatrixPixelGroup> pixelGroups(String pixelGroups) {
        return read("{\"pixelCount\": [3, 1, 1], \"pixelGroups\": " + pixelGroups + "}", FixtureMatrix.class).getPixelGroups();
    }

    @Test
    void takesThePixelGroupsInTheOrderOfTheBrowser() {
        // the names of array indices come first and by their number, like Object.keys gives them in the designer
        List<String> names = pixelGroups("{\"Master\": \"all\", \"2\": [\"2\"], \"10\": [\"3\"], \"1\": [\"1\"], \"01\": [\"1\"], \"Inner\": [\"2\"]}")
                .stream().map(FixtureMatrixPixelGroup::getName).toList();

        assertEquals(List.of("1", "2", "10", "Master", "01", "Inner"), names);
    }

    @Test
    void readsThePixelGroupsOfEarlierDesigners() {
        List<FixtureMatrixPixelGroup> groups = pixelGroups("""
                {
                  "wrapped all": {"isAll": "all", "constraints": null},
                  "wrapped keys": {"isAll": false, "constraints": ["1", "2"]},
                  "wrapped twice": {"isAll": {"isAll": false, "constraints": {"x": ["<=2"]}}, "constraints": null},
                  "all flag": {"isAll": true, "constraints": null},
                  "keys in an object": {"x": [], "keys": ["3"]},
                  "nothing": {"isAll": false, "constraints": null}
                }
                """);

        assertTrue(groups.get(0).isAll());
        assertEquals(List.of("1", "2"), groups.get(1).getConstraints().getKeys());
        assertFalse(groups.get(2).isAll());
        assertEquals(List.of("<=2"), groups.get(2).getConstraints().getX());
        assertTrue(groups.get(3).isAll());
        assertEquals(List.of("3"), groups.get(4).getConstraints().getKeys());

        // a group without a definition has no pixels (it had all of them before)
        assertFalse(groups.get(5).isAll());
        assertNull(groups.get(5).getConstraints());
    }

}
