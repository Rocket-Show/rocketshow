package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fanning a value out over the fixtures of a preset: each fixture takes its own value by its place in the fan, from 0
 * (the first fixture) to 1 (the last one). The place is where the fixture is in the order of the preset, or where it is
 * on the stage (see Preset.fanOrder). A dimmer, a zoom or any other value with a percentage is fanned by stops along
 * the fan, each of red, green and blue as well, which spans a gradient of colors over the fixtures (and approximates it
 * on a color wheel). The pan and the tilt are fanned over a number of degrees (see PanTilt), an effect moves each
 * fixture as far as its place says (see EffectCurve.fanMode). The designer fans them the very same way (fan.ts).
 */
final class Fan {

    private Fan() {
    }

    private static boolean isNumber(Double value) {
        return value != null && !value.isNaN();
    }

    private static boolean isPanTilt(FixtureCapabilityValue value) {
        return value.getType() == FixtureCapability.FixtureCapabilityType.Pan || value.getType() == FixtureCapability.FixtureCapabilityType.Tilt;
    }

    // Where a fixture is in the fan of its preset, from 0 (the first one) to 1 (the last one), or null if the fixtures
    // can't be told apart: a single one, or all of them at the same place on the stage. A place on the stage (NaN if
    // the fixtures of the preset can't be told apart there) wins over the place in the order of the preset.
    static Double getFanPlace(Integer fixtureIndex, Integer fixtureCount, Double fanPlace) {
        if (fanPlace != null) {
            return isNumber(fanPlace) ? fanPlace : null;
        }

        if (fixtureCount == null || !(fixtureCount >= 2)) {
            return null;
        }

        return (double) (fixtureIndex == null ? 0 : fixtureIndex) / (fixtureCount - 1);
    }

    static Double getFanPlace(PanTilt.Context context) {
        if (context == null) {
            return null;
        }

        return getFanPlace(context.fixtureIndex(), context.fixtureCount(), context.fanPlace());
    }

    // Where each of the parts of a preset at the passed places on the stage (in cm, y up, the front of the stage
    // towards z) is in a fan in the passed order, from 0 to 1, NaN for all of them if they are all at the same place
    static List<Double> getStageFanPlaces(List<double[]> positions, String order) {
        List<Double> coordinates = new ArrayList<>();

        for (double[] position : positions) {
            coordinates.add("front-back".equals(order) ? -position[2] : "bottom-top".equals(order) ? position[1] : position[0]);
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (double coordinate : coordinates) {
            min = Math.min(min, coordinate);
            max = Math.max(max, coordinate);
        }

        List<Double> places = new ArrayList<>();

        for (double coordinate : coordinates) {
            places.add(max > min ? (coordinate - min) / (max - min) : Double.NaN);
        }

        return places;
    }

    // does the preset fan its fixtures by where they are on the stage?
    static boolean isStageFanOrder(String order) {
        return "left-right".equals(order) || "front-back".equals(order) || "bottom-top".equals(order);
    }

    // is the value fanned out over the fixtures by stops of its own?
    static boolean isFanned(FixtureCapabilityValue value) {
        return value != null && value.getFan() != null && !value.getFan().isEmpty();
    }

    // The value of a fan at a place along it: the one of a stop right there, in between the stops around it, the first
    // and the last one beyond them. Of two stops at the same place (a hard edge), the first one is taken right there. A
    // fixture which can't be told apart from the others is in the middle.
    static double getFanValue(List<FanStop> stops, Double place) {
        return getFanValue(stops, place, null);
    }

    // the same with how the fan goes from one stop to the next: "steps", the stop of the share of the fan the place is
    // in, each stop taking as much of the fan as the others, without blending into the next one
    static double getFanValue(List<FanStop> stops, Double place, String between) {
        double position = place == null ? 0.5 : place;
        List<FanStop> sorted = new ArrayList<>(stops);
        sorted.sort(Comparator.comparingDouble(FanStop::getPosition));

        if ("steps".equals(between)) {
            return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(position * sorted.size()))).getValuePercentage();
        }

        if (position <= sorted.get(0).getPosition()) {
            return sorted.get(0).getValuePercentage();
        }

        for (int i = 1; i < sorted.size(); i++) {
            if (position == sorted.get(i).getPosition()) {
                return sorted.get(i).getValuePercentage();
            }

            if (position < sorted.get(i).getPosition()) {
                FanStop from = sorted.get(i - 1);
                FanStop to = sorted.get(i);

                return from.getValuePercentage() + (to.getValuePercentage() - from.getValuePercentage()) * ((position - from.getPosition()) / (to.getPosition() - from.getPosition()));
            }
        }

        return sorted.get(sorted.size() - 1).getValuePercentage();
    }

    // The offset of a fixture in a fan spread over a number of degrees (or of percent) around its middle: from the
    // first to the last one by half of them to either side, or from the one in the middle by half of them to one side
    // out to both ends by half of them to the other. A fixture which can't be told apart from the others is in the
    // middle.
    static double getSpreadOffset(Double spread, String shape, Double place) {
        if (!isNumber(spread)) {
            return 0;
        }

        if ("center".equals(shape)) {
            double position = place == null ? 0.5 : place;

            return spread * (Math.abs(2 * position - 1) - 0.5);
        }

        if (place == null) {
            return 0;
        }

        return spread * (place - 0.5);
    }

    // How much of its movement an effect makes on a fixture by its place in the fan (see EffectCurve.fanMode): from -1
    // to 1, a fixture which can't be told apart from the others not moving at all
    static double getFanFactor(String fanMode, Double place) {
        if (!"linear".equals(fanMode) && !"center".equals(fanMode) && !"side".equals(fanMode)) {
            return 1;
        }

        if (place == null) {
            return 0;
        }

        if ("linear".equals(fanMode)) {
            return 2 * place - 1;
        }

        return "center".equals(fanMode) ? Math.abs(2 * place - 1) : place;
    }

    private static boolean says(FixtureCapabilityValue value) {
        return isFanned(value) || (value.getValuePercentage() != null && value.getValuePercentage() >= 0);
    }

    // Does a preset travelling from the one value to the other work it out for each fixture on its own: a value with a
    // percentage travelling from a fan or into one (from one gradient of colors to the next, or from a single color to
    // a gradient)? Both have to say what to put on the fixtures, and neither is a slot of a wheel, which can't stand in
    // between two of them. The pan and the tilt travel in degrees (see PanTilt).
    static boolean isFanTransition(FixtureCapabilityValue from, FixtureCapabilityValue to) {
        if (isPanTilt(to)) {
            return false;
        }

        if (to.getSlotNumber() != null || from.getSlotNumber() != null) {
            return false;
        }

        return (isFanned(from) || isFanned(to)) && says(from) && says(to);
    }

    // the percentage a value puts on a fixture at a place in the fan: of its stops, if it is fanned
    private static Double getPlacedPercentage(FixtureCapabilityValue value, Double place) {
        return isFanned(value) ? getFanValue(value.getFan(), place, value.getFanBetween()) : value.getValuePercentage();
    }

    // Does the value need to be worked out for each fixture on its own: a fan, or travelling from or into one? Not the
    // pan and the tilt, which are worked out where they are played (see PanTilt).
    private static boolean isPlaced(FixtureCapabilityValue value) {
        if (isPanTilt(value)) {
            return false;
        }

        return isFanned(value) || (value.getTransitionFrom() != null && isFanned(value.getTransitionFrom()));
    }

    // The values a preset puts on one fixture: the fans worked out for the place of the fixture, travelling from the
    // fan of the step before (or into one) for each fixture on its own. Everything else is left as it is: the pan and
    // the tilt are worked out where they are played (see PanTilt). The passed values are returned as they are if
    // nothing is fanned.
    static List<FixtureCapabilityValue> getFixtureFanValues(List<FixtureCapabilityValue> values, PanTilt.Context context) {
        if (values.stream().noneMatch(Fan::isPlaced)) {
            return values;
        }

        Double place = getFanPlace(context);
        List<FixtureCapabilityValue> placedValues = new ArrayList<>(values.size());

        for (FixtureCapabilityValue value : values) {
            if (!isPlaced(value)) {
                placedValues.add(value);
                continue;
            }

            FixtureCapabilityValue placed = value.copy();
            placed.setFan(null);
            placed.setFanBetween(null);
            placed.setTransitionFrom(null);
            placed.setTransitionPosition(0);

            if (value.getTransitionFrom() != null) {
                double from = getPlacedPercentage(value.getTransitionFrom(), place);
                double to = getPlacedPercentage(value, place);
                placed.setValuePercentage(from + (to - from) * value.getTransitionPosition());
            } else {
                placed.setValuePercentage(getFanValue(value.getFan(), place, value.getFanBetween()));
            }

            placedValues.add(placed);
        }

        return placedValues;
    }
}
