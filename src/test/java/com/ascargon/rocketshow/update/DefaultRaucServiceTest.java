package com.ascargon.rocketshow.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading the output of RAUC: the slot a unit booted from decides, whether an update succeeded.
 */
class DefaultRaucServiceTest {

    @Test
    void readsTheSlotTheUnitBootedFrom() {
        assertEquals("rootfs.1", DefaultRaucService.getBootedSlot("""
                === System Info ===
                Compatible:  rocketshow-spark
                Variant:
                Booted from: rootfs.1 (B)

                === Bootloader ===
                Activated: rootfs.1 (B)

                === Slot States ===
                o [rootfs.1] (/dev/mmcblk0p3, ext4, booted)
                      bootname: B
                      boot status: good

                x [rootfs.0] (/dev/mmcblk0p2, ext4, inactive)
                      bootname: A
                      boot status: good
                """));

        // Older releases of RAUC print a compact status
        assertEquals("rootfs.0", DefaultRaucService.getBootedSlot("Compatible: rocketshow-spark\r\nBooted from: rootfs.0\r\nActivated: rootfs.0 (A)\r\n"));
    }

    @Test
    void readsNoSlotWithoutRauc() {
        assertNull(DefaultRaucService.getBootedSlot(""));
        assertNull(DefaultRaucService.getBootedSlot("bash: rauc: command not found"));
    }

    @Test
    void readsTheProgressOfAnInstallation() {
        List<String> output = List.of(
                "installing",
                "  0% Installing",
                "  0% Determining slot states",
                " 20% Determining slot states done.",
                " 40% Checking bundle done.",
                " 80% Copying image to rootfs.1",
                "100% Installing done.",
                "idle",
                "Installing `https://example.com/rocketshow.raucb` succeeded");

        List<String> progress = output.stream()
                .map(DefaultRaucService::getProgress)
                .filter(Objects::nonNull)
                .map(updateState -> Math.round(updateState.getProgressPercentage()) + " " + updateState.getProgressMessage())
                .toList();

        assertEquals(List.of(
                "0 Installing",
                "0 Determining slot states",
                "20 Determining slot states",
                "40 Checking bundle",
                "80 Copying image to rootfs.1",
                "100 Installing"), progress);
    }

    @Test
    void tellsWhyAnInstallationFailed() {
        List<String> output = List.of(
                "installing",
                "  0% Installing",
                "  0% Determining slot states",
                " 20% Determining slot states done.",
                " 20% Checking bundle",
                " 20% Verifying signature",
                " 40% Verifying signature failed.",
                " 40% Checking bundle failed.",
                "100% Installing failed.",
                "LastError: Failed verifying signature: signature verification failed: certificate has expired",
                "idle",
                "Installing `https://example.com/rocketshow.raucb` failed");

        assertEquals("Failed verifying signature: signature verification failed: certificate has expired", DefaultRaucService.getFailureReason(output, 1));
    }

    @Test
    void tellsWhatRaucWroteWithoutLastError() {
        assertEquals("Failed to connect to the D-Bus service of RAUC",
                DefaultRaucService.getFailureReason(List.of("Failed to connect to the D-Bus service of RAUC", ""), 1));
        assertEquals("RAUC exited with exit code 127", DefaultRaucService.getFailureReason(List.of(), 127));
    }

}
