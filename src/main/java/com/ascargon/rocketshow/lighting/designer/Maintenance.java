package com.ascargon.rocketshow.lighting.designer;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What the fixture pool of the designer asks a fixture to do on the live preview, through a maintenance capability of
 * its profile: reset itself (e.g. a moving head which lost its position) or switch its lamp on. It is held over
 * everything the presets do with the fixture for as long as the designer asks for it. The designer plays it the very
 * same way (maintenance.ts).
 */
final class Maintenance {

    enum Action {
        RESET,
        LAMP_ON
    }

    /**
     * The channel and the capability of a fixture doing an action.
     */
    record Found(CachedFixtureChannel channel, CachedFixtureCapability capability) {
    }

    // a maintenance capability doing nothing but the action, and one doing it among other things
    private static final Map<Action, Pattern> EXACTLY = Map.of(
            Action.RESET, Pattern.compile("^(fixture |total |full )?reset( all)?$"),
            Action.LAMP_ON, Pattern.compile("^lamp on$")
    );

    private static final Map<Action, Pattern> AMONG = Map.of(
            Action.RESET, Pattern.compile("\\breset\\b"),
            Action.LAMP_ON, Pattern.compile("\\blamp\\s*on\\b")
    );

    private Maintenance() {
    }

    // the channel and the capability of a fixture doing the passed action, or null if it can't
    static Found getCapability(List<CachedFixtureChannel> channels, Action action) {
        Found found = null;

        for (CachedFixtureChannel channel : channels) {
            if (channel.getChannel() == null) {
                continue;
            }

            for (CachedFixtureCapability capability : channel.getCapabilities()) {
                String comment = capability.getCapability().getComment() == null ? "" : capability.getCapability().getComment().trim().toLowerCase();

                if (capability.getCapability().getType() != FixtureCapability.FixtureCapabilityType.Maintenance) {
                    continue;
                }

                if (EXACTLY.get(action).matcher(comment).find()) {
                    return new Found(channel, capability);
                }

                if (found == null && AMONG.get(action).matcher(comment).find()) {
                    found = new Found(channel, capability);
                }
            }
        }

        return found;
    }
}
