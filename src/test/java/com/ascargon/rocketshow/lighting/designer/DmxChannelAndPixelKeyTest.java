package com.ascargon.rocketshow.lighting.designer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a fixture (or a pixel of it) starts on the DMX universes: the fixtures at the same place are counted once (see
 * DefaultDesignerService.getPresetFixtureCount).
 */
class DmxChannelAndPixelKeyTest {

    private static DmxChannelAndPixelKey key(int dmxChannel, String pixelKey, String dmxUniverseUuid) {
        DmxChannelAndPixelKey key = new DmxChannelAndPixelKey();
        key.setDmxChannel(dmxChannel);
        key.setPixelKey(pixelKey);
        key.setDmxUniverseUuid(dmxUniverseUuid);
        return key;
    }

    @Test
    void isTheSamePlaceOnlyWithTheSameChannelPixelAndUniverse() {
        DmxChannelAndPixelKey place = key(1, "1-1", "universe");

        assertEquals(place, place);
        assertEquals(place, key(1, "1-1", "universe"));
        assertEquals(place.hashCode(), key(1, "1-1", "universe").hashCode());

        assertNotEquals(place, key(2, "1-1", "universe"));
        assertNotEquals(place, key(1, "1-2", "universe"));
        assertNotEquals(place, key(1, "1-1", "other"));
        assertNotEquals(place, key(1, null, "universe"));
        assertNotEquals(key(1, null, "universe"), place);
        assertNotEquals(place, key(1, "1-1", null));
        assertNotEquals(key(1, "1-1", null), place);
        assertNotEquals(place, null);
        assertNotEquals(place, "1-1");

        // a fixture without pixels on a universe not set yet
        assertEquals(key(1, null, null), key(1, null, null));
        assertEquals(key(1, null, null).hashCode(), key(1, null, null).hashCode());
    }

    @Test
    void countsTheFixturesAtTheSamePlaceOnce() {
        Set<DmxChannelAndPixelKey> counted = new HashSet<>();

        assertTrue(counted.add(key(1, null, "universe")));
        assertFalse(counted.add(key(1, null, "universe")));
        assertTrue(counted.add(key(4, null, "universe")));
        assertTrue(counted.add(key(1, null, "other")));
        assertTrue(counted.add(key(1, "1-1", "universe")));
        assertEquals(4, counted.size());
    }

}
