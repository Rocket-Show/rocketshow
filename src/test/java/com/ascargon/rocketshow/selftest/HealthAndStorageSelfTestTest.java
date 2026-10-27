package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.health.HealthStatusSeverity;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The checks of the self-test which need no hardware: the health and the storage.
 */
class HealthAndStorageSelfTestTest {

    @TempDir
    Path directory;

    private final SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> false);

    private SelfTestCheck health(HealthStatusSeverity severity, String... reasons) {
        HealthStatus healthStatus = new HealthStatus();
        healthStatus.setHealthStatusSeverity(severity);
        healthStatus.getReasons().addAll(List.of(reasons));
        HealthService healthService = mock(HealthService.class);
        when(healthService.getHealthStatus()).thenReturn(healthStatus);

        new HealthSelfTest(healthService).run(context);

        return context.getChecks().getFirst();
    }

    @Test
    void aHealthyOrDegradedUnitPasses() {
        SelfTestCheck check = health(HealthStatusSeverity.DEGRADED, "EEPROM version date 2024-01-01 is older than 2025-03-10", "Less than 5% free disk space left");

        assertEquals(SelfTestOutcome.PASSED, check.getResult());
        assertEquals("DEGRADED: EEPROM version date 2024-01-01 is older than 2025-03-10; Less than 5% free disk space left", check.getDetails());
    }

    @Test
    void anUnhealthyUnitFails() {
        SelfTestCheck check = health(HealthStatusSeverity.FAIL_REBOOT_DEVICE, "OLA service is not active");

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("FAIL_REBOOT_DEVICE: OLA service is not active", check.getDetails());
    }

    private SettingsService settingsWithBasePath(Path basePath) {
        Settings settings = new Settings();
        settings.setBasePath(basePath.toString());
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        return settingsService;
    }

    @Test
    void writesAndReadsBackAFileInTheDataDirectory() throws Exception {
        new StorageSelfTest(settingsWithBasePath(directory)).run(context);

        SelfTestCheck check = context.getChecks().getFirst();
        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertTrue(check.getDetails().startsWith("1 MB written and read back in "), check.getDetails());

        // And deletes it
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void failsWhenTheDataDirectoryCannotBeWritten() throws Exception {
        new StorageSelfTest(settingsWithBasePath(directory.resolve("missing"))).run(context);

        SelfTestCheck check = context.getChecks().getFirst();
        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertTrue(check.getDetails().startsWith("Could not write and read back 1 MB: "), check.getDetails());
    }

}
