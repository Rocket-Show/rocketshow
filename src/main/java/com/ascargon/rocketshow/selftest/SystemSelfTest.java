package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.update.DefaultRaucService;
import com.ascargon.rocketshow.update.RaucService;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.DeviceInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What a ready to use unit relies on in the field: A/B updates, the watchdog, the read-only root file system. And the
 * clock of every unit, which the scheduler relies on.
 */
@Service
class SystemSelfTest implements SelfTestStep {

    private final static Logger logger = LoggerFactory.getLogger(SystemSelfTest.class);

    static final String UPDATES = "updates";
    static final String WATCHDOG = "watchdog";
    static final String READ_ONLY_ROOT = "read-only-root";
    static final String CLOCK = "clock";

    // A slot in "rauc status" without its colors, e.g. "x [rootfs.0] (/dev/mmcblk0p2, ext4, inactive)", where the locale
    // supports UTF-8 "⏺ [rootfs.0] (...)", or, of older releases of RAUC, "  rootfs.0: class=rootfs, device=/dev/mmcblk0p2"
    private static final Pattern SLOT_PATTERN = Pattern.compile("^\\s*(?:\\S\\s+)?\\[?(rootfs\\.\\d+)]?[:\\s(]");
    // RAUC colors the slots and their boot status, also when not printing to a terminal
    private static final Pattern COLOR_PATTERN = Pattern.compile("\u001B\\[[0-9;]*m");
    private static final Pattern BOOT_STATUS_PATTERN = Pattern.compile("boot status[:=]\\s*(\\w+)");

    // How far the real-time clock may be off from the system's clock
    private static final long RTC_TOLERANCE_SECONDS = 60;

    private final DeviceInformationService deviceInformationService;
    private final RaucService raucService;
    private final VersionService versionService;
    private final SelfTestSystem system;

    SystemSelfTest(DeviceInformationService deviceInformationService, RaucService raucService, VersionService versionService, SelfTestSystem system) {
        this.deviceInformationService = deviceInformationService;
        this.raucService = raucService;
        this.versionService = versionService;
        this.system = system;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(UPDATES, WATCHDOG, READ_ONLY_ROOT, CLOCK);
    }

    @Override
    public void run(SelfTestContext context) {
        if (deviceInformationService.getDeviceInformation().isAvailable()) {
            context.report(checkUpdates());
            context.report(checkWatchdog());
            context.report(checkReadOnlyRoot());
        } else {
            context.report(SelfTestCheck.skipped(UPDATES, null, "Only ready to use units update with A/B slots"));
            context.report(SelfTestCheck.skipped(WATCHDOG, null, "Only ready to use units run with a watchdog"));
            context.report(SelfTestCheck.skipped(READ_ONLY_ROOT, null, "Only ready to use units run with a read-only root file system"));
        }

        context.report(checkClock(Instant.now()));
    }

    private SelfTestCheck checkUpdates() {
        String raucStatus;
        try {
            raucStatus = raucService.getStatus();
        } catch (Exception e) {
            return SelfTestCheck.failed(UPDATES, null, "Could not run rauc status: " + e.getMessage());
        }

        SelfTestCheck check = evaluateRaucStatus(raucStatus);
        if (check.getResult() != SelfTestOutcome.PASSED) {
            logger.warn("Output of rauc status:\n{}", raucStatus);
        }
        return check;
    }

    // Two slots to update into, and the slot running marked good
    static SelfTestCheck evaluateRaucStatus(String raucStatus) {
        Map<String, String> bootStatuses = new LinkedHashMap<>();
        String slot = null;

        for (String line : COLOR_PATTERN.matcher(raucStatus).replaceAll("").split("\\R")) {
            Matcher slotMatcher = SLOT_PATTERN.matcher(line);
            if (slotMatcher.find()) {
                slot = slotMatcher.group(1);
                bootStatuses.putIfAbsent(slot, "unknown");
            }

            Matcher bootStatusMatcher = BOOT_STATUS_PATTERN.matcher(line);
            if (slot != null && bootStatusMatcher.find()) {
                bootStatuses.put(slot, bootStatusMatcher.group(1));
            }
        }

        String bootedSlot = DefaultRaucService.getBootedSlot(raucStatus);
        String slots = bootStatuses.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue()).collect(Collectors.joining(", "));

        if (bootStatuses.size() < 2) {
            // Without a status, the output is rather an error of RAUC. The whole output goes into the log.
            String output = raucStatus.contains("===") ? "none" : raucStatus.strip().lines().findFirst().orElse("none");
            return SelfTestCheck.failed(UPDATES, bootedSlot, "Not 2 slots to update into: " + (slots.isEmpty() ? output : slots));
        }

        if (bootedSlot == null) {
            return SelfTestCheck.failed(UPDATES, null, "RAUC doesn't tell the slot the unit booted from: " + slots);
        }

        if ("bad".equals(bootStatuses.get(bootedSlot))) {
            return SelfTestCheck.failed(UPDATES, bootedSlot, "The slot running is marked bad: " + slots);
        }

        return SelfTestCheck.passed(UPDATES, bootedSlot, slots);
    }

    private SelfTestCheck checkWatchdog() {
        String hardware = "";
        try {
            String output = system.run("systemctl", "show", "--property=RuntimeWatchdogUSec").output();
            String timeout = output.substring(output.indexOf('=') + 1).trim();
            hardware = timeout.isEmpty() || timeout.equals("0") || timeout.equals("infinity")
                    ? ", no hardware watchdog for the system"
                    : ", the hardware watchdog reboots a hung system after " + timeout;
        } catch (Exception e) {
            logger.debug("Could not read the hardware watchdog of systemd", e);
        }

        Optional<String> watchdogMicros = system.getenv("WATCHDOG_USEC");
        if (watchdogMicros.isEmpty()) {
            return SelfTestCheck.failed(WATCHDOG, null, "systemd doesn't watch the app (no WatchdogSec in its service)" + hardware);
        }

        try {
            long seconds = Long.parseLong(watchdogMicros.get().trim()) / 1_000_000;
            return SelfTestCheck.passed(WATCHDOG, null, "systemd restarts the app when it doesn't answer for " + seconds + " s" + hardware);
        } catch (NumberFormatException e) {
            return SelfTestCheck.failed(WATCHDOG, null, "systemd gives the app an unknown watchdog timeout: " + watchdogMicros.get());
        }
    }

    private SelfTestCheck checkReadOnlyRoot() {
        String root = null;

        // The last mount of / counts
        for (String line : system.read("/proc/mounts").orElse("").split("\\R")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 4 && fields[1].equals("/")) {
                root = line.trim();
            }
        }

        if (root == null) {
            return SelfTestCheck.failed(READ_ONLY_ROOT, null, "/ is not in /proc/mounts");
        }

        String[] fields = root.split("\\s+");
        String target = fields[2] + " on " + fields[0];

        if (List.of(fields[3].split(",")).contains("ro")) {
            return SelfTestCheck.passed(READ_ONLY_ROOT, target, "/ is read-only");
        }

        return SelfTestCheck.failed(READ_ONLY_ROOT, target, "/ is writable: a power cut can damage the system");
    }

    // The clock is right, as far as the unit can tell. Package visible for the tests.
    SelfTestCheck checkClock(Instant now) {
        Instant second = now.truncatedTo(ChronoUnit.SECONDS);

        try {
            Date built = versionService.getCurrentVersionInfo().getDate();
            if (built != null && now.isBefore(built.toInstant())) {
                return SelfTestCheck.failed(CLOCK, second.toString(), "Before this software was built (" + built.toInstant().truncatedTo(ChronoUnit.SECONDS) + ")");
            }
        } catch (Exception e) {
            logger.debug("Could not read the date of the software", e);
        }

        String ntpSynchronized = null;
        try {
            SelfTestSystem.CommandResult result = system.run("timedatectl", "show", "--property=NTPSynchronized", "--value");
            if (result.exitCode() == 0) {
                ntpSynchronized = result.output();
            }
        } catch (Exception e) {
            logger.debug("Could not ask timedatectl whether the clock is synchronized", e);
        }

        boolean realTimeClock = system.exists("/sys/class/rtc/rtc0");
        String realTimeClockName = system.read("/sys/class/rtc/rtc0/name").map(String::trim).map(name -> "real-time clock " + name).orElse("real-time clock");

        if ("yes".equals(ntpSynchronized)) {
            return SelfTestCheck.passed(CLOCK, second.toString(), "Synchronized with a time server" + (realTimeClock ? ", " + realTimeClockName : ""));
        }

        if (realTimeClock) {
            Optional<Long> realTimeClockSeconds = system.read("/sys/class/rtc/rtc0/since_epoch").map(String::trim).map(Long::parseLong);
            if (realTimeClockSeconds.isEmpty()) {
                return SelfTestCheck.failed(CLOCK, second.toString(), "Not synchronized, the " + realTimeClockName + " can't be read");
            }

            long offSeconds = Math.abs(Duration.between(Instant.ofEpochSecond(realTimeClockSeconds.get()), now).getSeconds());
            if (offSeconds > RTC_TOLERANCE_SECONDS) {
                return SelfTestCheck.failed(CLOCK, second.toString(), "Not synchronized, the " + realTimeClockName + " is " + offSeconds + " s off (battery empty?)");
            }

            return SelfTestCheck.passed(CLOCK, second.toString(), "Not synchronized, the " + realTimeClockName + " agrees");
        }

        return SelfTestCheck.passed(CLOCK, second.toString(), ntpSynchronized == null
                ? "Can't tell whether it's synchronized, no real-time clock"
                : "Not synchronized, no real-time clock: scheduled compositions may start at the wrong time until a time server answers");
    }

}
