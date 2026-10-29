package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.update.RaucService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import com.ascargon.rocketshow.util.DiskSpace;
import com.ascargon.rocketshow.util.DiskSpaceService;
import com.ascargon.rocketshow.util.EepromService;
import com.ascargon.rocketshow.util.ErrorLogService;
import com.ascargon.rocketshow.util.TemperatureService;
import com.ascargon.rocketshow.video.HdmiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultHealthServiceTest {

    private final DiskSpaceService diskSpaceService = mock(DiskSpaceService.class);
    private final TemperatureService temperatureService = mock(TemperatureService.class);
    private final EepromService eepromService = mock(EepromService.class);
    private final Settings settings = new Settings();
    private DefaultHealthService healthService;

    @BeforeEach
    void setUp() throws Exception {
        HdmiService hdmiService = mock(HdmiService.class);
        OlaService olaService = mock(OlaService.class);
        ErrorLogService errorLogService = mock(ErrorLogService.class);
        VersionService versionService = mock(VersionService.class);
        RaucService raucService = mock(RaucService.class);
        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        SettingsService settingsService = mock(SettingsService.class);

        DiskSpace diskSpace = new DiskSpace();
        diskSpace.setUsedMB(50);
        diskSpace.setAvailableMB(950);

        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());
        when(versionService.getCurrentVersionInfo()).thenReturn(new VersionInfo());
        when(eepromService.getVersionDate()).thenReturn(LocalDate.of(2025, 6, 1));
        when(hdmiService.isConnected()).thenReturn(true);
        when(diskSpaceService.get()).thenReturn(diskSpace);
        when(temperatureService.get()).thenReturn(50.0);
        when(errorLogService.getLastLogs()).thenReturn(List.of());
        when(settingsService.getSettings()).thenReturn(settings);

        healthService = new DefaultHealthService(
                hdmiService,
                diskSpaceService,
                temperatureService,
                olaService,
                errorLogService,
                versionService,
                raucService,
                deviceInformationService,
                eepromService,
                settingsService
        );
    }

    @Test
    void isOkWithoutProblems() {
        HealthStatus healthStatus = healthService.getHealthStatus();

        assertEquals(HealthStatusSeverity.OK, healthStatus.getHealthStatusSeverity());
        assertEquals(List.of(), healthStatus.getReasons());
        assertEquals(95, healthStatus.getFreeDiskSpacePercentage());
    }

    @Test
    void getHealthStatusDegradesWhenEepromVersionDateIsTooOld() throws Exception {
        when(eepromService.getVersionDate()).thenReturn(LocalDate.of(2025, 3, 9));

        HealthStatus healthStatus = healthService.getHealthStatus();

        assertEquals(HealthStatusSeverity.DEGRADED, healthStatus.getHealthStatusSeverity());
        assertTrue(healthStatus.getReasons().contains(
                "EEPROM version date 2025-03-09 is older than 2025-03-10"
        ));
    }

    @Test
    void degradesWhenTheDiskIsAlmostFullOrTheUnitHot() throws Exception {
        DiskSpace diskSpace = new DiskSpace();
        diskSpace.setUsedMB(980);
        diskSpace.setAvailableMB(20);
        when(diskSpaceService.get()).thenReturn(diskSpace);
        when(temperatureService.get()).thenReturn(85.0);

        HealthStatus healthStatus = healthService.getHealthStatus();

        assertEquals(HealthStatusSeverity.DEGRADED, healthStatus.getHealthStatusSeverity());
        assertEquals(List.of("Less than 5% free disk space left", "High temperature. CPU throttling possible"), healthStatus.getReasons());
    }

    @Test
    void tellsWhichCheckFailed() throws Exception {
        when(temperatureService.get()).thenThrow(new RuntimeException("vcgencmd not found"));

        assertEquals(List.of("Could not check the temperature"), healthService.getHealthStatus().getReasons());
    }

    @Test
    void warnsAboutAnAccessPointLeftOpenByAShortPassphrase() {
        settings.setWlanApEnable(true);
        settings.setWlanApPassphrase("123456");

        HealthStatus healthStatus = healthService.getHealthStatus();

        assertEquals(HealthStatusSeverity.DEGRADED, healthStatus.getHealthStatusSeverity());
        assertTrue(healthStatus.getReasons().getFirst().startsWith("The wireless access point is open"), healthStatus.getReasons().toString());
    }

    @Test
    void acceptsAnAccessPointOpenOnPurposeOrProtected() {
        settings.setWlanApEnable(true);

        settings.setWlanApPassphrase("");
        assertEquals(HealthStatusSeverity.OK, healthService.getHealthStatus().getHealthStatusSeverity());

        settings.setWlanApPassphrase("12345678");
        assertEquals(HealthStatusSeverity.OK, healthService.getHealthStatus().getHealthStatusSeverity());

        settings.setWlanApEnable(false);
        settings.setWlanApPassphrase("123");
        assertEquals(HealthStatusSeverity.OK, healthService.getHealthStatus().getHealthStatusSeverity());
    }

}
