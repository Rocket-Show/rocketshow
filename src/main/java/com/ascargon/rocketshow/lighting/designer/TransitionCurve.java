package com.ascargon.rocketshow.lighting.designer;

/**
 * How a value moves from where it is to where it is heading: the curve maps the linear
 * progress of a transition (0 = not started, 1 = arrived) to the part of the distance
 * already covered. The preset and scene fades run it as well, which is why the same
 * curve shapes a fade in, a fade out and a step transition alike.
 *
 * @author Moritz A. Vieli
 */
public final class TransitionCurve {

    private TransitionCurve() {
    }

    public static double apply(String curveType, double position) {
        // outside the transition there is nothing to shape (this also catches NaN)
        if (!(position > 0)) {
            return 0;
        }

        if (position >= 1) {
            return 1;
        }

        if (curveType == null) {
            return position;
        }

        switch (curveType) {
            case "ease-in":
                // creeps away from the old value and accelerates into the new one
                return position * position;
            case "ease-out":
                // jumps away from the old value and settles into the new one
                return 1 - (1 - position) * (1 - position);
            case "ease-in-out":
                // slow at both ends, fastest in the middle
                return position < 0.5 ? 2 * position * position : 1 - Math.pow(-2 * position + 2, 2) / 2;
            case "snap":
                // holds the old value for the whole transition and jumps at the end of it
                return 0;
            default:
                // "linear", and anything an older project may carry
                return position;
        }
    }
}
