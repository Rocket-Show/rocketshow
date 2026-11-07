package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The order the steps of a preset are played in. "forward" plays them the way they are written down, the others take
 * each step for as long as it lasts written down, in another order:
 * <ul>
 * <li>"backward" plays them from the last one to the first</li>
 * <li>"pingpong" plays them forward and back again, without playing the steps at both ends twice in a row</li>
 * <li>"random" plays each of them once per pass, shuffled anew for every pass, and never the same step twice in a
 * row</li>
 * </ul>
 * <p>
 * This mirrors preset-steps-order of the Rocket Show Designer, which the designer's own preview runs.
 *
 * @author Moritz A. Vieli
 */
public final class PresetStepsOrder {

    private PresetStepsOrder() {
    }

    // whether the steps are played in another order than they are written down in (an order this Rocket Show doesn't
    // know plays them forward, like in the designer)
    public static boolean isOrdered(Preset preset) {
        String order = preset.getStepsOrder();
        return "backward".equals(order) || "pingpong".equals(order) || "random".equals(order);
    }

    // How long each step lasts the way the steps are written down: until the next one starts, the last one for the
    // rest of a pass.
    public static double[] getStepLengths(Preset preset) {
        List<PresetStep> steps = preset.getSteps();

        if (steps == null || steps.isEmpty()) {
            return new double[0];
        }

        double loopEndMillis = steps.get(0).getStartMillis() + PresetSteps.getStepsLoopMillis(preset);
        double[] lengths = new double[steps.size()];

        for (int i = 0; i < steps.size(); i++) {
            double endMillis = i < steps.size() - 1 ? steps.get(i + 1).getStartMillis() : loopEndMillis;
            lengths[i] = endMillis - steps.get(i).getStartMillis();
        }

        return lengths;
    }

    // the steps which last any time, which are the ones a sequence played in another order reaches
    private static List<Integer> getPlayableIndices(double[] lengths) {
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < lengths.length; i++) {
            if (lengths[i] > 0) {
                indices.add(i);
            }
        }

        return indices;
    }

    // The playable steps shuffled for a pass, the same for the same seed and pass: from the last place to the first,
    // each place swaps with one of the places before it or itself.
    private static List<Integer> shuffle(List<Integer> indices, double seed, double pass) {
        List<Integer> shuffled = new ArrayList<>(indices);

        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = (int) Math.floor(PixelMath.hash(PixelMath.toInt(pass), PixelMath.toInt(seed), i) * (i + 1));
            Collections.swap(shuffled, i, j);
        }

        return shuffled;
    }

    // The steps a pass of the sequence plays, by their index in the preset, in the order they are played in. Only a
    // random order is different from one pass to the next.
    public static List<Integer> getOrderIndices(Preset preset, double pass) {
        List<Integer> indices = getPlayableIndices(getStepLengths(preset));
        String order = preset.getStepsOrder();

        if ("backward".equals(order)) {
            Collections.reverse(indices);
            return indices;
        }

        if ("pingpong".equals(order)) {
            // back again without the last step, which was just played. A sequence which starts over leaves out the
            // first step as well, which the next pass opens with. One which doesn't ends on it.
            List<Integer> back = new ArrayList<>(indices.subList(0, Math.max(indices.size() - 1, 0)));
            Collections.reverse(back);

            if (preset.isStepsLoop() && !back.isEmpty()) {
                back.remove(back.size() - 1);
            }

            List<Integer> played = new ArrayList<>(indices);
            played.addAll(back);
            return played;
        }

        if ("random".equals(order)) {
            if (indices.size() < 3) {
                // two steps can only take turns
                return indices;
            }

            List<Integer> shuffled = shuffle(indices, preset.getStepsSeed(), pass);

            // A pass doesn't open with the step the one before it ended with. Swapping the first two leaves the last
            // step of a pass where it is, so the pass before can be shuffled on its own to find it.
            int previousLast = shuffle(indices, preset.getStepsSeed(), pass - 1).get(indices.size() - 1);

            if (shuffled.get(0) == previousLast) {
                shuffled.set(0, shuffled.get(1));
                shuffled.set(1, previousLast);
            }

            return shuffled;
        }

        return indices;
    }

    public static List<Integer> getOrderIndices(Preset preset) {
        return getOrderIndices(preset, 0);
    }

    // The length of a pass of the sequence: the time it takes to play the steps once in their order, which is what a
    // sequence which loops starts over after.
    public static double getPassMillis(Preset preset) {
        if (!isOrdered(preset)) {
            return PresetSteps.getStepsLoopMillis(preset);
        }

        double[] lengths = getStepLengths(preset);
        double passMillis = 0;

        for (int index : getOrderIndices(preset)) {
            passMillis += lengths[index];
        }

        return passMillis;
    }
}
