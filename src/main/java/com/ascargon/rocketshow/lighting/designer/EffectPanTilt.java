package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A movement of the moving heads of a preset around where the preset points them (or around the middle, if it
 * doesn't): a circle, a figure 8, a line back and forth or a wave (back and forth on the pan, nodding three times on
 * the tilt on the way), its size given in degrees on each axis, which moves every model the same way. It runs on the
 * timing of a curve: its period, its phase, the chase over the fixtures, how long it runs and how far it moves each
 * fixture by its place in the fan of the preset (a line fanned "linear" opens and closes the fan of the heads, see
 * EffectCurve.fanMode). The designer plays it the very same way (effect-pan-tilt.ts).
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


    // How far the passed fixture is moved away from where the preset points it, or null if the movement does not apply
    // at this moment. Fanned, it moves as far as its place in the fan of the preset says (its place on the stage, if the
    // preset fans by it, see Fan).
    public Offset getOffsetAtMillis(double timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        return getOffsetAtMillis(timeMillis, fixtureIndex, fixtureCount, beatsPerMinute, null);
    }

    public Offset getOffsetAtMillis(double timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute, Double fanPlace) {
        Double cyclePosition = this.getCyclePositionAtMillis(timeMillis, fixtureIndex, fixtureCount, beatsPerMinute);

        if (cyclePosition == null) {
            return null;
        }

        Offset shapePosition = this.getShapePosition(cyclePosition);
        double fan = this.getFanFactor(Fan.getFanPlace(fixtureIndex, fixtureCount, fanPlace));

        return new Offset(sizePanDegrees / 2 * shapePosition.pan() * fan, sizeTiltDegrees / 2 * shapePosition.tilt() * fan);
    }

    public String getShape() {
        return shape;
    }

    // a value missing or null keeps the default, like in the designer
    public void setShape(String shape) {
        this.shape = shape == null || shape.isEmpty() ? "circle" : shape;
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
