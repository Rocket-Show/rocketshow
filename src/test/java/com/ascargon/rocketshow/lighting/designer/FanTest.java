package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Values, gradients of colors and effects fanned out over the fixtures of a preset, like fan.ts of the designer (the
 * conformance cases play them on whole fixtures).
 */
class FanTest {

    private static FixtureCapabilityValue value(FixtureCapability.FixtureCapabilityType type, Double valuePercentage, FanStop... stops) {
        FixtureCapabilityValue value = new FixtureCapabilityValue();
        value.setType(type);
        value.setValuePercentage(valuePercentage);
        value.setFan(stops.length == 0 ? null : List.of(stops));
        return value;
    }

    @Test
    void placesTheFixturesInTheOrderOfThePresetOrOnTheStage() {
        assertEquals(0, Fan.getFanPlace(0, 3, null));
        assertEquals(0.5, Fan.getFanPlace(1, 3, null));
        assertEquals(1, Fan.getFanPlace(2, 3, null));

        // a single fixture can't be told apart from others
        assertNull(Fan.getFanPlace(0, 1, null));
        assertNull(Fan.getFanPlace(0, null, null));

        // its place on the stage wins, NaN where the fixtures can't be told apart there
        assertEquals(0.25, Fan.getFanPlace(2, 3, 0.25));
        assertNull(Fan.getFanPlace(2, 3, Double.NaN));

        // from the left to the right, from the front (towards z) to the back, from the bottom to the top
        List<double[]> positions = List.of(new double[]{-100, 0, 200}, new double[]{100, 300, -200}, new double[]{0, 100, 0});
        assertEquals(List.of(0.0, 1.0, 0.5), Fan.getStageFanPlaces(positions, "left-right"));
        assertEquals(List.of(0.0, 1.0, 0.5), Fan.getStageFanPlaces(positions, "front-back"));
        assertEquals(1.0 / 3, Fan.getStageFanPlaces(positions, "bottom-top").get(2), 1e-12);

        // all at the same place
        assertTrue(Fan.getStageFanPlaces(List.of(new double[]{0, 0, 0}, new double[]{0, 0, 0}), "left-right").get(0).isNaN());

        assertTrue(Fan.isStageFanOrder("left-right"));
        assertFalse(Fan.isStageFanOrder("preset"));
    }

    @Test
    void readsTheValueOfAFanBetweenItsStops() {
        List<FanStop> stops = List.of(FanStop.of(1, 1), FanStop.of(0, 0.2));

        assertEquals(0.2, Fan.getFanValue(stops, 0d));
        assertEquals(0.6, Fan.getFanValue(stops, 0.5), 1e-12);
        assertEquals(1, Fan.getFanValue(stops, 1d));

        // a fixture which can't be told apart from the others is in the middle
        assertEquals(0.6, Fan.getFanValue(stops, null), 1e-12);

        // from the middle out to both ends
        List<FanStop> center = List.of(FanStop.of(0, 1), FanStop.of(0.5, 0), FanStop.of(1, 1));
        assertEquals(0.5, Fan.getFanValue(center, 0.25), 1e-12);
        assertEquals(0, Fan.getFanValue(center, null));
    }

    @Test
    void spreadsAndMovesTheFixturesByTheirPlace() {
        assertEquals(-30, Fan.getSpreadOffset(60d, null, 0d));
        assertEquals(30, Fan.getSpreadOffset(60d, "linear", 1d));
        assertEquals(0, Fan.getSpreadOffset(60d, "linear", null));
        assertEquals(-30, Fan.getSpreadOffset(60d, "center", 0.5));
        assertEquals(30, Fan.getSpreadOffset(60d, "center", 0d));
        assertEquals(-30, Fan.getSpreadOffset(60d, "center", null));
        assertEquals(0, Fan.getSpreadOffset(null, "center", 0d));

        assertEquals(1, Fan.getFanFactor("none", 0d));
        assertEquals(-1, Fan.getFanFactor("linear", 0d));
        assertEquals(0.5, Fan.getFanFactor("center", 0.25));
        assertEquals(0.25, Fan.getFanFactor("side", 0.25));
        assertEquals(0, Fan.getFanFactor("side", null));
    }

    @Test
    void worksOutTheFansForAFixture() {
        FixtureCapabilityValue dimmer = value(FixtureCapability.FixtureCapabilityType.Intensity, 0.6, FanStop.of(0, 0.2), FanStop.of(1, 1));
        FixtureCapabilityValue zoom = value(FixtureCapability.FixtureCapabilityType.Zoom, 0.3);
        List<FixtureCapabilityValue> values = List.of(dimmer, zoom);

        List<FixtureCapabilityValue> last = Fan.getFixtureFanValues(values, new PanTilt.Context(2, 3, null));
        assertEquals(1, last.get(0).getValuePercentage());
        assertNull(last.get(0).getFan());
        assertSame(zoom, last.get(1));

        // nothing fanned: the values as they are
        List<FixtureCapabilityValue> plain = List.of(zoom);
        assertSame(plain, Fan.getFixtureFanValues(plain, new PanTilt.Context(2, 3, null)));

        // travelling from a value into a fan, for each fixture on its own
        FixtureCapabilityValue travelling = dimmer.copy();
        travelling.setTransitionFrom(value(FixtureCapability.FixtureCapabilityType.Intensity, 0d));
        travelling.setTransitionPosition(0.5);
        assertEquals(0.1, Fan.getFixtureFanValues(List.of(travelling), new PanTilt.Context(0, 3, null)).get(0).getValuePercentage(), 1e-12);
        assertEquals(0.5, Fan.getFixtureFanValues(List.of(travelling), new PanTilt.Context(2, 3, null)).get(0).getValuePercentage(), 1e-12);
    }

    @Test
    void tellsWhichValuesTravelForEachFixture() {
        FixtureCapabilityValue fanned = value(FixtureCapability.FixtureCapabilityType.Intensity, 0.6, FanStop.of(0, 0.2), FanStop.of(1, 1));
        FixtureCapabilityValue plain = value(FixtureCapability.FixtureCapabilityType.Intensity, 0.3);

        assertTrue(Fan.isFanTransition(plain, fanned));
        assertTrue(Fan.isFanTransition(fanned, plain));
        assertFalse(Fan.isFanTransition(plain, plain));
        assertFalse(Fan.isFanTransition(value(FixtureCapability.FixtureCapabilityType.Intensity, null), fanned));
    }

    @Test
    void readsAFanOfAProject() {
        FixtureCapabilityValue value = new ObjectMapper().readValue("""
                {"type": "ColorIntensity", "color": "Red", "valuePercentage": 0.5, "fan": [{"position": 0, "valuePercentage": 0}, {"position": 1}]}
                """, FixtureCapabilityValue.class);

        assertEquals(2, value.getFan().size());
        assertEquals(1, value.getFan().get(1).getPosition());
        assertEquals(0, value.getFan().get(1).getValuePercentage());

        // a fan without stops is none
        assertNull(new ObjectMapper().readValue("{\"type\": \"Intensity\", \"fan\": []}", FixtureCapabilityValue.class).getFan());
    }
}
