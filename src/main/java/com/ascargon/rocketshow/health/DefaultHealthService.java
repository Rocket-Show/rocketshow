package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.update.RaucService;
import com.ascargon.rocketshow.update.VersionInfo;
import com.ascargon.rocketshow.update.VersionService;
import com.ascargon.rocketshow.util.*;
import com.ascargon.rocketshow.video.HdmiService;
import org.apache.logging.log4j.core.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class DefaultHealthService implements HealthService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultHealthService.class);
    private static final LocalDate EEPROM_MIN_VERSION_DATE = LocalDate.of(2025, 3, 10);

    private final HdmiService hdmiService;
    private final DiskSpaceService diskSpaceService;
    private final TemperatureService temperatureService;
    private final OlaService olaService;
    private final ErrorLogService errorLogService;
    private final VersionService versionService;
    private final RaucService raucService;
    private final DeviceInformationService deviceInformationService;
    private final EepromService eepromService;
    private final SystemTestService systemTestService;

    public DefaultHealthService(
            HdmiService hdmiService,
            DiskSpaceService diskSpaceService,
            TemperatureService temperatureService,
            OlaService olaService,
            ErrorLogService errorLogService,
            VersionService versionService,
            RaucService raucService,
            DeviceInformationService deviceInformationService,
            EepromService eepromService,
            SystemTestService systemTestService
    ) {
        this.hdmiService = hdmiService;
        this.diskSpaceService = diskSpaceService;
        this.temperatureService = temperatureService;
        this.olaService = olaService;
        this.errorLogService = errorLogService;
        this.versionService = versionService;
        this.raucService = raucService;
        this.deviceInformationService = deviceInformationService;
        this.eepromService = eepromService;
        this.systemTestService = systemTestService;
    }

    private void addReason(HealthStatus healthStatus, String reason) {
        healthStatus.getReasons().add(reason);
    }

    private void setDegraded(HealthStatus healthStatus) {
        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK) {
            healthStatus.setHealthStatusSeverity(HealthStatusSeverity.DEGRADED);
        }
    }

    private void setFailRestartApp(HealthStatus healthStatus) {
        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK
                || healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.DEGRADED) {

            healthStatus.setHealthStatusSeverity(HealthStatusSeverity.FAIL_RESTART_APP);
        }
    }

    private void setFailRebootDevice(HealthStatus healthStatus) {
        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK
                || healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.DEGRADED
                || healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.FAIL_RESTART_APP) {

            healthStatus.setHealthStatusSeverity(HealthStatusSeverity.FAIL_REBOOT_DEVICE);
        }
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Override
    public HealthStatus getHealthStatus() {
        HealthStatus healthStatus = new HealthStatus();
        DeviceInformation deviceInformation = deviceInformationService.getDeviceInformation();

        healthStatus.setDeviceInformation(deviceInformation);

        try {
            VersionInfo currentVersionInfo = versionService.getCurrentVersionInfo();
            healthStatus.setSoftwareVersion(currentVersionInfo.getVersion());
            healthStatus.setSoftwareDate(currentVersionInfo.getDate());
        } catch (Exception e) {
            logger.error("Could not read software version", e);
        }

        try {
            LocalDate eepromVersionDate = eepromService.getVersionDate();
            healthStatus.setEepromVersionDate(toDate(eepromVersionDate));

            if (eepromVersionDate.isBefore(EEPROM_MIN_VERSION_DATE)) {
                setDegraded(healthStatus);
                addReason(healthStatus, "EEPROM version date " + eepromVersionDate
                        + " is older than " + EEPROM_MIN_VERSION_DATE);
            }
        } catch (Exception e) {
            setDegraded(healthStatus);
            addReason(healthStatus, "Could not check EEPROM version");
            logger.error("Could not check EEPROM version", e);
        }

//        if (!hdmiService.isConnected()) {
//            addReason(healthStatus, "HDMI is not connected");
//        }

        try {
            double used = diskSpaceService.get().getUsedMB();
            double available = diskSpaceService.get().getAvailableMB();

            double freePercent = (used + available) > 0
                    ? (available / (used + available)) * 100.0
                    : 0.0;

            healthStatus.setFreeDiskSpacePercentage(Math.round(freePercent));

            if (healthStatus.getFreeDiskSpacePercentage() < 5) {
                setDegraded(healthStatus);
                addReason(healthStatus, "Less than 5% free disk space left");
            }
        } catch (Exception e) {
            setDegraded(healthStatus);
            addReason(healthStatus, "Could not check free disk space");
        }

        try {
            healthStatus.setTemperature(temperatureService.get());
            if (healthStatus.getTemperature() > 80) {
                setDegraded(healthStatus);
                addReason(healthStatus, "High temperature. CPU throttling possible");
            }
        } catch (Exception e) {
            setDegraded(healthStatus);
            addReason(healthStatus, "Could not check free disk space");
        }

        String olaError = olaService.getHealthyError();
        if (olaError != null) {
            setDegraded(healthStatus);
            addReason(healthStatus, olaError);
        }

        List<LogEvent> errorList = errorLogService.getLastLogs();
        healthStatus.setRecentErrorRate(errorList.size());
        int start = Math.max(0, errorList.size() - 3);
        for (int i = start; i < errorList.size(); i++) {
            LogEvent event = errorList.get(i);
            addReason(healthStatus, "Error log: " + event.getMessage().getFormattedMessage());
        }

        if (deviceInformation.isAvailable()) {
            healthStatus.setRaucSlot(raucService.getCurrentSlot());
        }

        // TODO check for updates and warn, if no internet connection
        // TODO gather system information (os version, memory, etc.)
        // TODO show GPIO input status
        // TODO show networking info (WIFI, Accesspoint and ethernet)
        // TODO show connected USB devices

        return healthStatus;
    }

    @Override
    public SystemTestResult testSystem() {
        return systemTestService.runSystemTest();
    }

}
