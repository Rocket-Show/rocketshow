package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.lighting.OlaService;
import com.ascargon.rocketshow.video.HdmiService;
import org.apache.logging.log4j.core.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Service
public class DefaultHealthService implements HealthService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultHealthService.class);

    private final HdmiService hdmiService;
    private final DiskSpaceService diskSpaceService;
    private final TemperatureService temperatureService;
    private final OlaService olaService;
    private final ErrorLogService errorLogService;
    private final UpdateService updateService;

    public DefaultHealthService(
            HdmiService hdmiService,
            DiskSpaceService diskSpaceService,
            TemperatureService temperatureService,
            OlaService olaService,
            ErrorLogService errorLogService,
            UpdateService updateService
    ) {
        this.hdmiService = hdmiService;
        this.diskSpaceService = diskSpaceService;
        this.temperatureService = temperatureService;
        this.olaService = olaService;
        this.errorLogService = errorLogService;
        this.updateService = updateService;
    }

    private void addReason(HealthStatus healthStatus, String reason) {
        healthStatus.getReasons().add(reason);
    }

    private void setDegraded(HealthStatus healthStatus) {
        if (healthStatus.getSeverity() == Severity.OK) {
            healthStatus.setSeverity(Severity.DEGRADED);
        }
    }

    private void setFailRestartApp(HealthStatus healthStatus) {
        if (healthStatus.getSeverity() == Severity.OK
                || healthStatus.getSeverity() == Severity.DEGRADED) {

            healthStatus.setSeverity(Severity.FAIL_RESTART_APP);
        }
    }

    private void setFailRebootDevice(HealthStatus healthStatus) {
        if (healthStatus.getSeverity() == Severity.OK
                || healthStatus.getSeverity() == Severity.DEGRADED
                || healthStatus.getSeverity() == Severity.FAIL_RESTART_APP) {

            healthStatus.setSeverity(Severity.FAIL_REBOOT_DEVICE);
        }
    }

    @Override
    public HealthStatus getHealthStatus() {
        HealthStatus healthStatus = new HealthStatus();

        try {
            healthStatus.setSoftwareVersion(updateService.getCurrentVersionInfo().getVersion());
            healthStatus.setSoftwareDate(updateService.getCurrentVersionInfo().getDate());
        } catch (Exception e) {
            logger.error("Could not read software version", e);
        }

        if (!hdmiService.isConnected()) {
            addReason(healthStatus, "HDMI is not connected");
        }

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

        // TODO show serial, SKU or whether no data is available
        // TODO check for updates and warn, if no internet connection
        // TODO gather system information (os version, memory, etc.)
        // TODO show GPIO input status
        // TODO show networking info (WIFI, Accesspoint and ethernet)
        // TODO show connected USB devices

        return healthStatus;
    }

    @Override
    public void testSystem() {
        // TODO play the default composition and check for errors
        // TODO periodically change GPIO output status (e.g. each 3 seconds)
        // TODO Send MIDI messages, expect the same MIDI messages
    }

}