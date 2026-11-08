package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Rocket Show Designer fixture channel.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class FixtureChannel {

    private List<String> fineChannelAliases = new ArrayList<>();
    private String defaultValue;
    private FixtureCapability capability;
    private List<FixtureCapability> capabilities;
    private String dmxValueResolution;

    private static final Pattern RESOLUTION = Pattern.compile("^(8|16|24)bit$");

    /**
     * How much the DMX values a profile gives a channel (the ranges of its capabilities and its default value) are
     * multiplied by to be in the resolution of the channel with all of its fine channels: 256 for a channel of 16 bits
     * whose values are written in 8 bits (dmxValueResolution "8bit"), 1 for one written in its full resolution, which
     * is what a profile not saying it does. The designer reads them the very same way (fixture-channel.ts).
     */
    static double getResolutionMultiplier(FixtureChannel channel) {
        if (channel == null || channel.getDmxValueResolution() == null) {
            return 1;
        }

        Matcher matcher = RESOLUTION.matcher(channel.getDmxValueResolution());

        if (!matcher.matches()) {
            return 1;
        }

        double bytes = Double.parseDouble(matcher.group(1)) / 8;
        double fullBytes = 1 + (channel.getFineChannelAliases() == null ? 0 : channel.getFineChannelAliases().size());

        return bytes < fullBytes ? Math.pow(256, fullBytes - bytes) : 1;
    }

    /**
     * A capability with its range in the full resolution of its channel (see getResolutionMultiplier): from the first
     * value of the start of the range written to the last one of its end (8 bits [0, 127] are 16 bits [0, 32767]). The
     * capability itself, if it is written in the full resolution. The capability of the profile is left as it is.
     */
    static FixtureCapability getCapabilityInFullResolution(FixtureCapability capability, double multiplier) {
        if (multiplier == 1 || capability == null || capability.getDmxRange() == null || capability.getDmxRange().size() != 2) {
            return capability;
        }

        FixtureCapability scaled = new FixtureCapability();

        try {
            for (Field field : FixtureCapability.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.set(scaled, field.get(capability));
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not copy a capability", e);
        }

        List<Integer> range = capability.getDmxRange();
        scaled.setDmxRange(List.of((int) (range.get(0) * multiplier), (int) (range.get(1) * multiplier + multiplier - 1)));

        return scaled;
    }
}
