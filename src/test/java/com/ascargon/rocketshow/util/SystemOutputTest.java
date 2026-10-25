package com.ascargon.rocketshow.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading what the system tells about itself: the disk space, the LAN, the device information file, and the
 * output of commands.
 */
class SystemOutputTest {

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void readsTheWholeOutputOfACommandWhileDebugging() throws Exception {
        Level level = LogManager.getLogger(ShellManager.class).getLevel();
        Configurator.setLevel(ShellManager.class.getName(), Level.DEBUG);

        try {
            for (int i = 0; i < 5; i++) {
                ShellManager shellManager = new ShellManager(new String[]{"seq", "1", "20000"}, false);
                String output = new String(shellManager.getProcess().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                shellManager.getProcess().waitFor();

                assertEquals(IntStream.rangeClosed(1, 20000).mapToObj(String::valueOf).collect(Collectors.joining("\n", "", "\n")), output);
            }
        } finally {
            Configurator.setLevel(ShellManager.class.getName(), level);
        }
    }

    @Test
    void readsTheDiskSpaceOfTheDataPartition() {
        String df = """
                Filesystem     1MB-blocks   Used Available Use% Mounted on
                /dev/mmcblk0p4    25042MB 5423MB   18303MB  23% /data
                """;

        DiskSpace diskSpace = DefaultDiskSpaceService.parse(df, "/data");

        assertEquals(5423, diskSpace.getUsedMB());
        assertEquals(18303, diskSpace.getAvailableMB());
    }

    @Test
    void readsNoDiskSpaceOfAnotherPartition() {
        DiskSpace diskSpace = DefaultDiskSpaceService.parse("""
                Filesystem     1MB-blocks  Used Available Use% Mounted on
                /dev/root         31166MB 5423MB  24435MB  19% /
                """, "/data");

        assertEquals(0, diskSpace.getUsedMB());
        assertEquals(0, diskSpace.getAvailableMB());
    }

    @Test
    void prefersTheAddressTheRouterLeased() {
        LanInfo lanInfo = DefaultLanService.parse(List.of(
                "IP4.ADDRESS[1]:169.254.10.20/16",
                "IP4.ADDRESS[2]:192.168.1.22/24",
                "IP4.GATEWAY:192.168.1.1",
                "IP4.DNS[1]:192.168.1.1",
                "IP4.DNS[2]:8.8.8.8",
                "DHCP4.OPTION[1]:dhcp_lease_time = 86400",
                "DHCP4.OPTION[2]:ip_address = 192.168.1.22",
                "DHCP4.OPTION[3]:subnet_mask = 255.255.255.0"));

        assertEquals("192.168.1.22", lanInfo.getIpAddress());
        assertEquals("255.255.255.0", lanInfo.getSubnetMask());
        assertEquals("192.168.1.1", lanInfo.getGateway());
        assertEquals("192.168.1.1", lanInfo.getDns1());
        assertEquals("8.8.8.8", lanInfo.getDns2());
    }

    @Test
    void readsAStaticAddress() {
        LanInfo lanInfo = DefaultLanService.parse(List.of(
                "IP4.ADDRESS[1]:10.0.0.5/8",
                "IP4.GATEWAY:--"));

        assertEquals("10.0.0.5", lanInfo.getIpAddress());
        assertEquals("255.0.0.0", lanInfo.getSubnetMask());
        assertEquals("", lanInfo.getGateway());
        assertEquals("", lanInfo.getDns1());
    }

    @Test
    void readsNoAddressWithoutCable() {
        LanInfo lanInfo = DefaultLanService.parse(List.of("Error: Device 'eth0' not found."));

        assertEquals("", lanInfo.getIpAddress());
        assertEquals("", lanInfo.getSubnetMask());
    }

    @Test
    void readsTheDeviceInformationItStores() {
        DeviceInformation deviceInformation = new DeviceInformation();
        deviceInformation.setFileVersion("1.0");
        deviceInformation.setCountry("CH");
        deviceInformation.setSerial("SPK-000123");
        deviceInformation.setModel("Spark One");
        deviceInformation.setHardwareRevision("B=2");
        deviceInformation.setSku("SPK1-EU");

        DeviceInformation read = DefaultDeviceInformationService.parse(DefaultDeviceInformationService.format(deviceInformation).lines().toList());

        assertTrue(read.isAvailable());
        assertEquals("1.0", read.getFileVersion());
        assertEquals("CH", read.getCountry());
        assertEquals("SPK-000123", read.getSerial());
        assertEquals("Spark One", read.getModel());
        assertEquals("B=2", read.getHardwareRevision());
        assertEquals("SPK1-EU", read.getSku());
    }

    @Test
    void skipsCommentsAndUnknownKeys() {
        DeviceInformation read = DefaultDeviceInformationService.parse(List.of(
                "# Provisioned at the factory",
                "",
                "SERIAL = SPK-000124 ",
                "CALIBRATION=42",
                "broken line"));

        assertEquals("SPK-000124", read.getSerial());
        assertFalse(read.getModel() != null && !read.getModel().isEmpty());
    }

}
