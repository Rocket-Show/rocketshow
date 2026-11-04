package com.ascargon.rocketshow.lighting.designer;

/**
 * An effect of a type this Rocket Show does not know, e.g. one a newer designer added. It lights nothing, so the rest of
 * the preset still plays, and it is reported when the project is loaded.
 */
public class EffectUnknown extends Effect {

    // the type the designer gave it
    private String type;

    @Override
    public Double getValueAtMillis(double timeMillis, Integer fixtureIndex, Integer fixtureCount, Double beatsPerMinute) {
        return null;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
