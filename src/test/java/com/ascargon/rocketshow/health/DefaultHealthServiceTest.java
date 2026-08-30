package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.lighting.OlaService;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultHealthServiceTest {

    @Test
    void getHealthStatusDegradesWhenEepromVersionDateIsTooOld() throws Exception {
        HdmiService hdmiService = mock(HdmiService.class);
        DiskSpaceService diskSpaceService = mock(DiskSpaceService.class);
        TemperatureService temperatureService = mock(TemperatureService.class);
        OlaService olaService = mock(OlaService.class);
        ErrorLogService errorLogService = mock(ErrorLogService.class);
        VersionService versionService = mock(VersionService.class);
        RaucService raucService = mock(RaucService.class);
        DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
        EepromService eepromService = mock(EepromService.class);
        SystemTestService systemTestService = mock(SystemTestService.class);

        DiskSpace diskSpace = new DiskSpace();
        diskSpace.setUsedMB(50);
        diskSpace.setAvailableMB(950);

        when(deviceInformationService.getDeviceInformation()).thenReturn(new DeviceInformation());
        when(versionService.getCurrentVersionInfo()).thenReturn(new VersionInfo());
        when(eepromService.getVersionDate()).thenReturn(LocalDate.of(2025, 3, 9));
        when(hdmiService.isConnected()).thenReturn(true);
        when(diskSpaceService.get()).thenReturn(diskSpace);
        when(temperatureService.get()).thenReturn(50.0);
        when(errorLogService.getLastLogs()).thenReturn(List.of());

        DefaultHealthService healthService = new DefaultHealthService(
                hdmiService,
                diskSpaceService,
                temperatureService,
                olaService,
                errorLogService,
                versionService,
                raucService,
                deviceInformationService,
                eepromService,
                systemTestService
        );

        HealthStatus healthStatus = healthService.getHealthStatus();

        assertEquals(HealthStatusSeverity.DEGRADED, healthStatus.getHealthStatusSeverity());
        assertTrue(healthStatus.getReasons().contains(
                "EEPROM version date 2025-03-09 is older than 2025-03-10"
        ));
    }

}
