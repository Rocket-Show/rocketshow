package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A movement of the moving heads of a preset around where the preset points them (or around the middle, if it
 * doesn't): a circle, a figure 8, a line back and forth or a wave (back and forth on the pan, nodding three times on
 * the tilt on the way), its size given in degrees on each axis, which moves every model the same way. It runs on the
 * timing of a curve: its period, its phase, the chase over the fixtures and how long it runs. It can be fanned over
 * the heads as well, each one moving as far as its place in the row of the preset says: a line fanned from the middle
 * opens and closes the fan of the heads. The designer plays it the very same way (effect-pan-tilt.ts).
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EffectPanTilt extends EffectCurve {

    /**
     * How far a movement moves a fixture away from where the preset points it, in degrees on each axis.
     */
    public record Offset(double pan, double tilt) {
    }

    private String shape = "circle";

    // how far the movement reaches from one side to the other, in degrees
    private double sizePanDegrees = 60;
    private double sizeTiltDegrees = 30;

    // how far each head moves by its place in the row of the preset:
    // "none" moves all of them the same way,
    // "center" fans the movement out from the middle, like a step fans the heads out: the first and the last head move
    // the whole size, to opposite sides, the one in the middle doesn't move. A line opens and closes the fan of the
    // step (and crosses the heads over, beyond it), a circle turns them the other way round on the other side.
    // "side" fans it out from the first head, which doesn't move, to the last one, moving the whole size.
    private String fanMode = "none";

    // where the shape is at a position inside its cycle (between 0 and 1), from -1 to 1 on each axis
    public Offset getShapePosition(double cyclePosition) {
        double twice = (cyclePosition * 2) % 1;
        double thrice = (cyclePosition * 3) % 1;

        return switch (shape) {
            case "figure8" -> new Offset(CycleSine.sin(cyclePosition), CycleSine.sin(twice));
            case "line" -> new Offset(CycleSine.sin(cyclePosition), CycleSine.sin(cyclePosition));
            case "wave" -> new Offset(CycleSine.sin(cyclePosition), CycleSine.sin(thrice));
            // a circle: the cosine is the sine a quarter of a cycle ahead
            default -> new Offset(CycleSine.sin(cyclePosition), CycleSine.sin((cyclePosition + 0.25) % 1));
        };
    }

    // whether the heads move by their place in the row of the preset
    @JsonIgnore
    public boolean isFanned() {
        return "center".equals(fanMode) || "side".equals(fanMode);
    }

    // How much of the movement the passed fixture makes, by its place in the row of the preset (see fanMode): from -1
    // to 1 fanned from the middle, from 0 to 1 fanned from the first fixture. A single fixture is the middle and the
    // first one at once, it doesn't move (like it isn't fanned out by a step).
    public double getFanFactor(Integer fixtureIndex, Integer fixtureCount) {
        if (!isFanned()) {
            return 1;
        }

        if (fixtureCount == null || fixtureCount < 2) {
            return 0;
        }

        double place = (double) (fixtureIndex == null ? 0 : fixtureIndex) / (fixtureCount - 1);

        return "center".equals(fanMode) ? 2 * place - 1 : place;
    }

    // how far the passed fixture is moved away from where the preset points it, or null if the movement does not apply
    // at this moment
    public Offset getOffsetAtMillis(double timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        Double cyclePosition = this.getCyclePositionAtMillis(timeMillis, fixtureIndex, fixtureCount, beatsPerMinute);

        if (cyclePosition == null) {
            return null;
        }

        Offset shapePosition = this.getShapePosition(cyclePosition);
        double fan = this.getFanFactor(fixtureIndex, fixtureCount);

        return new Offset(sizePanDegrees / 2 * shapePosition.pan() * fan, sizeTiltDegrees / 2 * shapePosition.tilt() * fan);
    }

    public String getShape() {
        return shape;
    }

    // a value missing or null keeps the default, like in the designer
    public void setShape(String shape) {
        this.shape = shape == null || shape.isEmpty() ? "circle" : shape;
    }

    public String getFanMode() {
        return fanMode;
    }

    // a value missing or null moves all the heads the same way, like in the designer (and like movements always did)
    public void setFanMode(String fanMode) {
        this.fanMode = fanMode == null || fanMode.isEmpty() ? "none" : fanMode;
    }

    public double getSizePanDegrees() {
        return sizePanDegrees;
    }

    public void setSizePanDegrees(Double sizePanDegrees) {
        this.sizePanDegrees = sizePanDegrees == null ? 60 : sizePanDegrees;
    }

    public double getSizeTiltDegrees() {
        return sizeTiltDegrees;
    }

    public void setSizeTiltDegrees(Double sizeTiltDegrees) {
        this.sizeTiltDegrees = sizeTiltDegrees == null ? 30 : sizeTiltDegrees;
    }
}
