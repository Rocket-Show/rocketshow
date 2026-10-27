package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.update.RaucService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The checks of what a ready to use unit relies on (updates, watchdog, read-only root), the clock, the network and
 * the power, on the files and commands of a simulated unit.
 */
class SystemAndNetworkSelfTestTest {

    @TempDir
    Path root;

    private TestSelfTestSystem system;
    private final DeviceInformation deviceInformation = new DeviceInformation();
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
    private final RaucService raucService = mock(RaucService.class);
    private final VersionService versionService = mock(VersionService.class);
    private final VersionInfo versionInfo = new VersionInfo();
    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);

    private static final Instant NOW = Instant.parse("2026-10-27T13:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        system = new TestSelfTestSystem(root);
        deviceInformation.setAvailable(true);
        when(deviceInformationService.getDeviceInformation()).thenReturn(deviceInformation);
        when(raucService.getStatus()).thenReturn(raucStatus("good", "good"));
        when(versionService.getCurrentVersionInfo()).thenReturn(versionInfo);
        when(settingsService.getSettings()).thenReturn(settings);
    }

    private SystemSelfTest systemSelfTest() {
        return new SystemSelfTest(deviceInformationService, raucService, versionService, system);
    }

    private SelfTestContext run(SelfTestStep step) throws Exception {
        SelfTestContext context = new SelfTestContext(new SelfTestRequest(), () -> false);
        step.run(context);
        return context;
    }

    private static SelfTestCheck check(SelfTestContext context, String name) {
        List<SelfTestCheck> checks = context.getChecks().stream().filter(check -> check.getName().equals(name)).toList();
        assertEquals(1, checks.size(), context.getChecks().toString());
        return checks.getFirst();
    }

    private static void assertCheck(SelfTestOutcome result, String target, String details, SelfTestCheck check) {
        assertEquals(result, check.getResult(), check.toString());
        assertEquals(target, check.getTarget(), check.toString());
        assertEquals(details, check.getDetails(), check.toString());
    }

    @Test
    void aCommunityUnitSkipsTheFeaturesOfReadyToUseUnits() throws Exception {
        deviceInformation.setAvailable(false);

        SelfTestContext context = run(systemSelfTest());

        assertEquals(SelfTestOutcome.SKIPPED, check(context, SystemSelfTest.UPDATES).getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(context, SystemSelfTest.WATCHDOG).getResult());
        assertEquals(SelfTestOutcome.SKIPPED, check(context, SystemSelfTest.READ_ONLY_ROOT).getResult());
        // Every unit has a clock
        check(context, SystemSelfTest.CLOCK);
    }

    // What "rauc status" prints on a Rocket Show SPARK: colored also when not printing to a terminal, and the activated
    // slot marked with "⏺" and the others with "○" where the locale supports UTF-8, otherwise "x" and "o"
    private static String raucStatus(String bootedSlotMark, String otherSlotMark, String bootedSlotStatus, String otherSlotStatus) {
        return """
                === System Info ===
                Compatible:  spark1
                Variant:    \s
                Booted from: rootfs.1 (ROOTFS-B)

                === Bootloader ===
                Activated: rootfs.1 (ROOTFS-B)

                === Slot States ===
                %s \u001B[32m\u001B[32m\u001B[1m[rootfs.1]\u001B[0m (/dev/mmcblk0p5, ext4, \u001B[1mbooted\u001B[0m)
                      bootname: \u001B[34mROOTFS-B\u001B[0m
                      mounted: /
                      boot status: %s%s\u001B[0m
                    \u001B[32m\u001B[1m[bootfs.1]\u001B[0m (/dev/mmcblk0p4, vfat, active\u001B[0m)
                      mounted: /boot/firmware

                %s \u001B[1m[rootfs.0]\u001B[0m (/dev/mmcblk0p3, ext4, inactive\u001B[0m)
                      bootname: \u001B[34mROOTFS-A\u001B[0m
                      boot status: %s%s\u001B[0m
                    \u001B[1m[bootfs.0]\u001B[0m (/dev/mmcblk0p2, vfat, inactive\u001B[0m)

                """.formatted(bootedSlotMark, color(bootedSlotStatus), bootedSlotStatus, otherSlotMark, color(otherSlotStatus), otherSlotStatus);
    }

    private static String raucStatus(String bootedSlotStatus, String otherSlotStatus) {
        return raucStatus("⏺", "○", bootedSlotStatus, otherSlotStatus);
    }

    private static String color(String bootStatus) {
        return "good".equals(bootStatus) ? "\u001B[32m" : "\u001B[31m";
    }

    @Test
    void updatesIntoTheOtherSlot() {
        assertCheck(SelfTestOutcome.PASSED, "rootfs.1", "rootfs.1 good, rootfs.0 good", SystemSelfTest.evaluateRaucStatus(raucStatus("good", "good")));
        // A failed update marks the other slot bad, the next one installs into it again
        assertCheck(SelfTestOutcome.PASSED, "rootfs.1", "rootfs.1 good, rootfs.0 bad", SystemSelfTest.evaluateRaucStatus(raucStatus("good", "bad")));
        // Where the locale doesn't support UTF-8
        assertCheck(SelfTestOutcome.PASSED, "rootfs.1", "rootfs.1 good, rootfs.0 good", SystemSelfTest.evaluateRaucStatus(raucStatus("\u001B[1mx\u001B[0m", "o", "good", "good")));
    }

    @Test
    void readsTheStatusOfOlderReleasesOfRauc() {
        assertCheck(SelfTestOutcome.PASSED, "rootfs.0", "rootfs.0 good, rootfs.1 good", SystemSelfTest.evaluateRaucStatus("""
                Compatible: rocketshow-spark
                Booted from: rootfs.0 (A)
                Activated: rootfs.0 (A)
                slot states:
                  rootfs.0: class=rootfs, device=/dev/mmcblk0p2, type=ext4, bootname=A
                      state=booted, description=, parent=(none), mountpoint=/
                      boot status=good
                  rootfs.1: class=rootfs, device=/dev/mmcblk0p3, type=ext4, bootname=B
                      state=inactive, description=, parent=(none), mountpoint=(none)
                      boot status=good
                """));
    }

    @Test
    void failsWithoutTwoSlotsOrWithTheSlotRunningMarkedBad() throws Exception {
        assertCheck(SelfTestOutcome.FAILED, "rootfs.1", "The slot running is marked bad: rootfs.1 bad, rootfs.0 good", SystemSelfTest.evaluateRaucStatus(raucStatus("bad", "good")));
        // As copied from the terminal of the first unit checked, without the colors
        assertCheck(SelfTestOutcome.FAILED, "rootfs.1", "The slot running is marked bad: rootfs.1 bad, rootfs.0 bad", SystemSelfTest.evaluateRaucStatus("""
                === System Info ===
                Compatible:  spark1
                Variant:    \s
                Booted from: rootfs.1 (ROOTFS-B)

                === Bootloader ===
                Activated: rootfs.1 (ROOTFS-B)

                === Slot States ===
                ⏺ [rootfs.1] (/dev/mmcblk0p5, ext4, booted)
                      bootname: ROOTFS-B
                      mounted: /
                      boot status: bad
                    [bootfs.1] (/dev/mmcblk0p4, vfat, active)
                      mounted: /boot/firmware

                ○ [rootfs.0] (/dev/mmcblk0p3, ext4, inactive)
                      bootname: ROOTFS-A
                      boot status: bad
                    [bootfs.0] (/dev/mmcblk0p2, vfat, inactive)
                """));
        assertCheck(SelfTestOutcome.FAILED, null, "Not 2 slots to update into: rauc: Failed to obtain status", SystemSelfTest.evaluateRaucStatus("rauc: Failed to obtain status\n"));
        // The whole output goes into the log
        assertCheck(SelfTestOutcome.FAILED, "rootfs.0", "Not 2 slots to update into: none", SystemSelfTest.evaluateRaucStatus("""
                === System Info ===
                Booted from:    rootfs.0 (A)

                === Slot States ===
                """));

        when(raucService.getStatus()).thenThrow(new IOException("Cannot run program \"rauc\""));
        assertCheck(SelfTestOutcome.FAILED, null, "Could not run rauc status: Cannot run program \"rauc\"", check(run(systemSelfTest()), SystemSelfTest.UPDATES));
    }

    @Test
    void systemdWatchesTheApp() throws Exception {
        system.environment.put("WATCHDOG_USEC", "60000000");
        system.command("RuntimeWatchdogUSec=15s", "systemctl", "show", "--property=RuntimeWatchdogUSec");

        assertCheck(SelfTestOutcome.PASSED, null, "systemd restarts the app when it doesn't answer for 60 s, the hardware watchdog reboots a hung system after 15s",
                check(run(systemSelfTest()), SystemSelfTest.WATCHDOG));
    }

    @Test
    void failsWhenSystemdDoesNotWatchTheApp() throws Exception {
        system.command("RuntimeWatchdogUSec=0", "systemctl", "show", "--property=RuntimeWatchdogUSec");

        assertCheck(SelfTestOutcome.FAILED, null, "systemd doesn't watch the app (no WatchdogSec in its service), no hardware watchdog for the system",
                check(run(systemSelfTest()), SystemSelfTest.WATCHDOG));
    }

    @Test
    void theRootFileSystemIsReadOnly() throws Exception {
        system.file("/proc/mounts", """
                /dev/root / ext4 rw,relatime 0 0
                proc /proc proc rw,nosuid,nodev,noexec,relatime 0 0
                /dev/mmcblk0p3 / ext4 ro,relatime 0 0
                /dev/mmcblk0p4 /data ext4 rw,relatime 0 0
                """);

        assertCheck(SelfTestOutcome.PASSED, "ext4 on /dev/mmcblk0p3", "/ is read-only", check(run(systemSelfTest()), SystemSelfTest.READ_ONLY_ROOT));
    }

    @Test
    void failsWhenTheRootFileSystemIsWritable() throws Exception {
        system.file("/proc/mounts", "/dev/mmcblk0p2 / ext4 rw,noatime 0 0\n");

        assertCheck(SelfTestOutcome.FAILED, "ext4 on /dev/mmcblk0p2", "/ is writable: a power cut can damage the system", check(run(systemSelfTest()), SystemSelfTest.READ_ONLY_ROOT));
    }

    @Test
    void theClockIsSynchronized() throws Exception {
        system.command("yes", "timedatectl", "show", "--property=NTPSynchronized", "--value");
        system.file("/sys/class/rtc/rtc0/name", "rpi-rtc\n");

        assertCheck(SelfTestOutcome.PASSED, "2026-10-27T13:00:00Z", "Synchronized with a time server, real-time clock rpi-rtc", systemSelfTest().checkClock(NOW));
    }

    @Test
    void theRealTimeClockKeepsTheTimeWithoutInternet() throws Exception {
        system.command("no", "timedatectl", "show", "--property=NTPSynchronized", "--value");
        system.file("/sys/class/rtc/rtc0/name", "rpi-rtc\n");
        system.file("/sys/class/rtc/rtc0/since_epoch", String.valueOf(NOW.getEpochSecond() - 2));

        assertCheck(SelfTestOutcome.PASSED, "2026-10-27T13:00:00Z", "Not synchronized, the real-time clock rpi-rtc agrees", systemSelfTest().checkClock(NOW));

        // E.g. its battery is empty
        system.file("/sys/class/rtc/rtc0/since_epoch", "86400");
        assertEquals("Not synchronized, the real-time clock rpi-rtc is " + (NOW.getEpochSecond() - 86400) + " s off (battery empty?)", systemSelfTest().checkClock(NOW).getDetails());
    }

    @Test
    void failsWhenTheClockIsBeforeTheSoftware() {
        versionInfo.setDate(Date.from(Instant.parse("2026-11-01T00:00:00Z")));

        assertCheck(SelfTestOutcome.FAILED, "2026-10-27T13:00:00Z", "Before this software was built (2026-11-01T00:00:00Z)", systemSelfTest().checkClock(NOW));
    }

    @Test
    void cannotTellWithoutTimedatectlAndRealTimeClock() {
        // E.g. a unit without systemd
        assertCheck(SelfTestOutcome.PASSED, "2026-10-27T13:00:00Z", "Can't tell whether it's synchronized, no real-time clock", systemSelfTest().checkClock(NOW));

        system.command("no", "timedatectl", "show", "--property=NTPSynchronized", "--value");
        assertEquals("Not synchronized, no real-time clock: scheduled compositions may start at the wrong time until a time server answers", systemSelfTest().checkClock(NOW).getDetails());
    }

    private NetworkSelfTest networkSelfTest() {
        return new NetworkSelfTest(settingsService, system);
    }

    // A network interface: physical or virtual, wireless or wired, with a link or not
    private void networkInterface(String name, boolean physical, boolean wireless, String carrier) throws IOException {
        system.directory("/sys/class/net/" + name);
        if (physical) {
            system.directory("/sys/class/net/" + name + "/device");
        }
        if (wireless) {
            system.directory("/sys/class/net/" + name + "/wireless");
        }
        if (carrier != null) {
            system.file("/sys/class/net/" + name + "/carrier", carrier + "\n");
        }
    }

    @Test
    void theWiredNetworkHasALink() throws Exception {
        networkInterface("lo", false, false, "1");
        networkInterface("eth0", true, false, "1");
        networkInterface("wlan0", true, true, "1");
        system.file("/sys/class/net/eth0/speed", "1000\n");
        system.addresses.put("eth0", List.of("192.168.1.50"));

        assertCheck(SelfTestOutcome.PASSED, "eth0", "Link at 1000 Mbit/s, 192.168.1.50", check(run(networkSelfTest()), NetworkSelfTest.LAN));
    }

    @Test
    void theWiredNetworkIsSkippedWithoutCable() throws Exception {
        networkInterface("eth0", true, false, "0");
        system.file("/sys/class/net/eth0/speed", "-1\n");

        assertCheck(SelfTestOutcome.SKIPPED, "eth0", "No link, no cable?", check(run(networkSelfTest()), NetworkSelfTest.LAN));
    }

    @Test
    void theWiredNetworkIsSkippedWithoutInterface() throws Exception {
        networkInterface("lo", false, false, "1");
        networkInterface("wlan0", true, true, "1");

        assertCheck(SelfTestOutcome.SKIPPED, null, "No wired network interface", check(run(networkSelfTest()), NetworkSelfTest.LAN));
    }

    @Test
    void theAccessPointRunsWhenEnabled() throws Exception {
        assertCheck(SelfTestOutcome.SKIPPED, null, "Disabled in the settings", check(run(networkSelfTest()), NetworkSelfTest.ACCESS_POINT));

        settings.setWlanApEnable(true);
        assertCheck(SelfTestOutcome.FAILED, "Rocket Show", "No wireless interface wlan0", check(run(networkSelfTest()), NetworkSelfTest.ACCESS_POINT));

        networkInterface("wlan0", true, true, "1");
        system.command(3, "inactive", "systemctl", "is-active", "hostapd");
        assertCheck(SelfTestOutcome.FAILED, "Rocket Show", "hostapd is inactive", check(run(networkSelfTest()), NetworkSelfTest.ACCESS_POINT));

        system.command("active", "systemctl", "is-active", "hostapd");
        assertCheck(SelfTestOutcome.PASSED, "Rocket Show", "hostapd is active on wlan0", check(run(networkSelfTest()), NetworkSelfTest.ACCESS_POINT));
    }

    @Test
    void underVoltageOrThrottlingFails() {
        assertCheck(SelfTestOutcome.PASSED, null, "No under-voltage or throttling since the start", PowerSelfTest.evaluate(0, null));
        assertCheck(SelfTestOutcome.FAILED, "4.85 V", "Under-voltage now, throttled now: the power supply or its cable is too weak",
                PowerSelfTest.evaluate(0x50005, "4.85 V"));
        assertCheck(SelfTestOutcome.FAILED, null, "Under-voltage since the start: the power supply or its cable is too weak",
                PowerSelfTest.evaluate(0x10000, null));
        assertCheck(SelfTestOutcome.FAILED, null, "Throttled since the start: too hot, the soft temperature limit was reached",
                PowerSelfTest.evaluate(0x40000 | 0x80000, null));
        assertCheck(SelfTestOutcome.PASSED, null, "No under-voltage or throttling since the start, the CPU frequency was capped",
                PowerSelfTest.evaluate(0x20000, null));
    }

    @Test
    void readsThePowerFromTheFirmware() throws Exception {
        assertCheck(SelfTestOutcome.SKIPPED, null, "vcgencmd is not available, e.g. not on a Raspberry Pi", check(run(new PowerSelfTest(system)), PowerSelfTest.POWER));

        // E.g. the app may not ask the firmware
        system.command(255, "VCHI initialization failed", "vcgencmd", "get_throttled");
        assertCheck(SelfTestOutcome.FAILED, null, "vcgencmd get_throttled answered: VCHI initialization failed", check(run(new PowerSelfTest(system)), PowerSelfTest.POWER));

        system.command("throttled=0x0", "vcgencmd", "get_throttled");
        system.command("EXT5V_V volt(24)=5.13428000V", "vcgencmd", "pmic_read_adc", "EXT5V_V");
        assertCheck(SelfTestOutcome.PASSED, "5.13 V", "No under-voltage or throttling since the start", check(run(new PowerSelfTest(system)), PowerSelfTest.POWER));
    }

}
