package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.health.DefaultHealthService;
import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.health.HealthStatusSeverity;
import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.update.RaucService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.video.HdmiService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The watchdog of the ready to use units: checks the health every 30 seconds and tells systemd the app is alive.
 */
class DefaultWatchdogServiceTest {

    // What the watchdog and the health checks log
    private static class Log implements ErrorLogService {

        final List<LogEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void add(LogEvent event) {
            if (event.getLoggerName().startsWith("com.ascargon.rocketshow.util.DefaultWatchdogService")
                    || event.getLoggerName().startsWith("com.ascargon.rocketshow.health")) {
                events.add(event.toImmutable());
            }
        }

        // Like the error log of the app
        @Override
        public List<LogEvent> getLastLogs() {
            List<LogEvent> errors = events.stream().filter(event -> event.getLevel().isMoreSpecificThan(Level.ERROR)).toList();
            return new ArrayList<>(errors.subList(Math.max(0, errors.size() - 50), errors.size()));
        }

        List<String> messages() {
            return events.stream().map(event -> event.getLevel() + " " + event.getMessage().getFormattedMessage()).toList();
        }

    }

    private final Log log = new Log();
    private final List<String> notifications = new CopyOnWriteArrayList<>();
    private final RebootService rebootService = mock(RebootService.class);
    private LoggerContext context;
    private ErrorLogAppender appender;

    @BeforeEach
    void setUp() {
        context = (LoggerContext) LogManager.getContext(false);
        appender = new ErrorLogAppender(log, "DefaultWatchdogServiceTest", null, null) {
            @Override
            public void append(LogEvent event) {
                log.add(event);
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
    }

    @AfterEach
    void tearDown() {
        context.getConfiguration().getRootLogger().removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
    }

    private DefaultWatchdogService watchdog(HealthService healthService) {
        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        DeviceInformation deviceInformation = new DeviceInformation();
        deviceInformation.setAvailable(true);
        when(deviceInformationService.getDeviceInformation()).thenReturn(deviceInformation);

        return new DefaultWatchdogService(healthService, rebootService, deviceInformationService, mock(OperatingSystemInformationService.class)) {
            @Override
            void notifySystemd(String... arguments) {
                notifications.add(String.join(" ", arguments));
            }
        };
    }

    private static HealthStatus health(HealthStatusSeverity severity, String... reasons) {
        HealthStatus healthStatus = new HealthStatus();
        healthStatus.setHealthStatusSeverity(severity);
        healthStatus.setReasons(new ArrayList<>(List.of(reasons)));
        return healthStatus;
    }

    @Test
    void tellsSystemdTheAppIsAlive() throws Exception {
        HealthService healthService = mock(HealthService.class);
        when(healthService.getHealthStatus()).thenReturn(health(HealthStatusSeverity.OK));

        DefaultWatchdogService watchdogService = watchdog(healthService);
        watchdogService.runWatchdog();
        watchdogService.runWatchdog();

        assertEquals(List.of("--ready --status=RocketShow ready", "WATCHDOG=1", "WATCHDOG=1"), notifications);
        verify(rebootService, never()).reboot();
    }

    @Test
    void doesNothingOnTheCommunityEdition() throws Exception {
        HealthService healthService = mock(HealthService.class);
        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());

        new DefaultWatchdogService(healthService, rebootService, deviceInformationService, mock(OperatingSystemInformationService.class)) {
            @Override
            void notifySystemd(String... arguments) {
                notifications.add(String.join(" ", arguments));
            }
        }.runWatchdog();

        assertEquals(List.of(), notifications);
        verify(healthService, never()).getHealthStatus();
    }

    @Test
    void logsAChangeOfADegradedHealthOnce() {
        HealthService healthService = mock(HealthService.class);
        when(healthService.getHealthStatus()).thenReturn(
                health(HealthStatusSeverity.DEGRADED, "High temperature"),
                health(HealthStatusSeverity.DEGRADED, "High temperature"),
                health(HealthStatusSeverity.DEGRADED, "High temperature", "Less than 5% free disk space left"),
                health(HealthStatusSeverity.DEGRADED, "High temperature", "Less than 5% free disk space left"),
                health(HealthStatusSeverity.OK),
                health(HealthStatusSeverity.OK));

        DefaultWatchdogService watchdogService = watchdog(healthService);
        for (int i = 0; i < 6; i++) {
            watchdogService.runWatchdog();
        }

        assertEquals(List.of(
                "WARN System health is degraded: [High temperature]",
                "WARN System health is degraded: [High temperature, Less than 5% free disk space left]",
                "INFO System health is OK again"
        ), log.messages().stream().filter(message -> message.contains("System health")).toList());
        // Still alive
        assertEquals(6, notifications.stream().filter("WATCHDOG=1"::equals).count());
    }

    @Test
    void keepsTheHealthOfADegradedUnitReadable() throws Exception {
        // E.g. a unit with an old EEPROM, checked for 10 minutes
        EepromService eepromService = mock(EepromService.class);
        when(eepromService.getVersionDate()).thenReturn(LocalDate.of(2025, 1, 1));
        DiskSpace diskSpace = new DiskSpace();
        diskSpace.setUsedMB(50);
        diskSpace.setAvailableMB(950);
        DiskSpaceService diskSpaceService = mock(DiskSpaceService.class);
        when(diskSpaceService.get()).thenReturn(diskSpace);
        TemperatureService temperatureService = mock(TemperatureService.class);
        when(temperatureService.get()).thenReturn(50.0);
        VersionService versionService = mock(VersionService.class);
        when(versionService.getCurrentVersionInfo()).thenReturn(new VersionInfo());
        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getSettings()).thenReturn(new Settings());

        DefaultHealthService healthService = new DefaultHealthService(mock(HdmiService.class), diskSpaceService, temperatureService, mock(OlaService.class), log, versionService, mock(RaucService.class), deviceInformationService, eepromService, settingsService);

        DefaultWatchdogService watchdogService = watchdog(healthService);
        for (int i = 0; i < 20; i++) {
            watchdogService.runWatchdog();
        }

        assertEquals(List.of("EEPROM version date 2025-01-01 is older than 2025-03-10"), healthService.getHealthStatus().getReasons());
        assertEquals(0, healthService.getHealthStatus().getRecentErrorRate());
        assertEquals(20, notifications.stream().filter("WATCHDOG=1"::equals).count());
    }

}
