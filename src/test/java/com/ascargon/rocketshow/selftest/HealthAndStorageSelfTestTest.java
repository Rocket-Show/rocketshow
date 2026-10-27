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

    private SelfTestCheck storage(Path basePath, TestSelfTestSystem system) throws Exception {
        new StorageSelfTest(settingsWithBasePath(basePath), system).run(context);
        return context.getChecks().getFirst();
    }

    @Test
    void writesAndReadsBackAFileInTheDataDirectory() throws Exception {
        TestSelfTestSystem system = new TestSelfTestSystem(directory.resolve("system"));
        system.command("", "dmesg", "--level=emerg,alert,crit,err");
        Path data = Files.createDirectories(directory.resolve("data"));

        SelfTestCheck check = storage(data, system);

        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertTrue(check.getDetails().matches("16 MB written at [\\d.]+ MB/s and read back (at [\\d.]+ MB/s|from the cache of the system), no errors in the kernel log"), check.getDetails());

        // And deletes it
        try (Stream<Path> files = Files.list(data)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void failsWhenTheDataDirectoryCannotBeWritten() throws Exception {
        SelfTestCheck check = storage(directory.resolve("missing"), new TestSelfTestSystem(directory));

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertTrue(check.getDetails().startsWith("Could not write and read back 16 MB: "), check.getDetails());
    }

    @Test
    void failsWithErrorsOfTheStorageInTheKernelLog() throws Exception {
        TestSelfTestSystem system = new TestSelfTestSystem(directory.resolve("system"));
        // Only root reads the kernel log with dmesg, the journal has it as well
        system.command(1, "dmesg: read kernel buffer failed: Operation not permitted", "dmesg", "--level=emerg,alert,crit,err");
        system.command("""
                        vc4-drm gpu: [drm] *ERROR* Failed to get the HDMI clock
                        mmc0: Timeout waiting for hardware interrupt.
                        EXT4-fs error (device mmcblk0p4): ext4_find_entry:1455: inode #2: comm rocketshow: reading directory lblock 0
                        """,
                "journalctl", "--dmesg", "--boot", "--priority=err", "--no-pager", "--quiet", "--output=cat");

        SelfTestCheck check = storage(directory, system);

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("2 errors of the storage in the kernel log, e.g. mmc0: Timeout waiting for hardware interrupt.", check.getDetails());
    }

    @Test
    void passesWhenTheKernelLogCannotBeRead() throws Exception {
        SelfTestCheck check = storage(directory, new TestSelfTestSystem(directory.resolve("system")));

        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertTrue(check.getDetails().endsWith(", the kernel log can't be read"), check.getDetails());
    }

    @Test
    void findsTheErrorsOfTheStorage() {
        assertEquals(List.of("mmc1: error -110 whilst initialising SD card", "Buffer I/O error on dev sda1, logical block 0", "blk_update_request: I/O error, dev sdb, sector 2048"),
                StorageSelfTest.getStorageErrors("""
                        mmc1: error -110 whilst initialising SD card
                        sdhci-iproc fe340000.mmc: unknown error
                        Buffer I/O error on dev sda1, logical block 0
                        brcmfmac: brcmf_c_preinit_dcmds: Firmware: error
                        blk_update_request: I/O error, dev sdb, sector 2048
                        """));
    }

}
