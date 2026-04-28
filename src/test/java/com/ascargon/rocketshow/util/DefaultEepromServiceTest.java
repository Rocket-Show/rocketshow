package com.ascargon.rocketshow.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultEepromServiceTest {

    @Test
    void parseVersionDateReadsBootloaderDate() {
        String output = """
                2025/03/10 12:34:56
                version 123456789abcdef
                update-time 1741606496
                capabilities 0x0000007f
                """;

        assertEquals(
                LocalDate.of(2025, 3, 10),
                DefaultEepromService.parseVersionDate(output)
        );
    }

    @Test
    void parseVersionDateRejectsMissingDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefaultEepromService.parseVersionDate("version 123456789abcdef")
        );
    }

}
