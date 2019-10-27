package com.ascargon.rocketshow.lighting.designer;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rocket Show Designer cached fixture channel.
 *
 * @author Moritz A. Vieli
 */
public class CachedFixtureChannel {

    // the corresponding channel
    private FixtureChannel channel;

    // the name of the channel
    private String name;

    // all channel capabilities
    private List<CachedFixtureCapability> capabilities = new ArrayList<>();

    // default value
    private double defaultValue;

    // maximum value
    private double maxValue;

    // a color wheel, if available
    private FixtureWheel colorWheel;

    public FixtureChannel getChannel() {
        return channel;
    }

    public void setChannel(FixtureChannel channel) {
        this.channel = channel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CachedFixtureCapability> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CachedFixtureCapability> capabilities) {
        this.capabilities = capabilities;
    }

    public double getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public FixtureWheel getColorWheel() {
        return colorWheel;
    }

    public void setColorWheel(FixtureWheel colorWheel) {
        this.colorWheel = colorWheel;
    }
}
