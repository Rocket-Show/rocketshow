package com.ascargon.rocketshow.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileNamesTest {

    @Test
    void acceptsNames() {
        for (String name : new String[]{"Song 1", "head_smashed_far_away.wav", "..intro.wav", "Über.mid", "a.b.c"}) {
            assertEquals(name, FileNames.check(name));
        }
    }

    @Test
    void rejectsNamesLeavingTheDirectory() {
        for (String name : new String[]{"../settings", "../../settings.xml", "compositions/../../x", "/etc/passwd", "a\\b", "..", ".", "", "a\0b"}) {
            assertThrows(IllegalArgumentException.class, () -> FileNames.check(name), name);
        }

        assertThrows(IllegalArgumentException.class, () -> FileNames.check(null));
    }

}
