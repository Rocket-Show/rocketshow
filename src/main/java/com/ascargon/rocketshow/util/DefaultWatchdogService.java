package com.ascargon.rocketshow.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DefaultWatchdogService implements WatchdogService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultWatchdogService.class);

    private final HealthService healthService;
    private final RebootService rebootService;
    private final DeviceInformationService deviceInformationService;

    public DefaultWatchdogService(HealthService healthService, RebootService rebootService, DeviceInformationService deviceInformationService, OperatingSystemInformationService operatingSystemInformationService) {
        this.healthService = healthService;
        this.rebootService = rebootService;
        this.deviceInformationService = deviceInformationService;

        if (!watchdogActive()) {
            logger.info("Watchdog is not activated");
            return;
        }

        try {
            ShellManager shellManager = new ShellManager(new String[]{"systemd-notify", "--ready", "--status=RocketShow ready"});
            shellManager.getProcess().waitFor();
            shellManager.close();

            logger.info("Watchdog is activated");
        } catch (Exception e) {
            logger.error("Watchdog could not send READY=1", e);
        }
    }

    private boolean watchdogActive() {
        if (!deviceInformationService.getDeviceInformation().isAvailable()) {
            // Only run for the ready to use version
            return false;
        }

        return true;
    }

    @Override
    @Scheduled(fixedRate = 30000)
    public void runWatchdog() {
        if (!watchdogActive()) {
            return;
        }

        logger.debug("Watchdog check is running....");
        checkSystemHealth();
        logger.debug("Watchdog check finished");
    }

    private void checkSystemHealth() {
        HealthStatus healthStatus = healthService.getHealthStatus();

        if (healthStatus.getSeverity() == Severity.FAIL_REBOOT_DEVICE) {
            logger.error("System is unhealthy -> Reboot required");
            logger.error(healthStatus.toFailureString());

            try {
                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not reboot device", e);
            }
        }

        if (healthStatus.getSeverity() == Severity.FAIL_RESTART_APP) {
            logger.error("System is unhealthy -> App restart required");
            logger.error(healthStatus.toFailureString());

            try {
                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not reboot device", e);
            }
        }

        if (healthStatus.getSeverity() == Severity.DEGRADED) {
            logger.error("System health is degraded");
            logger.error(healthStatus.toFailureString());

            try {
                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not reboot device", e);
            }
        }

        // Everything fine, pet the watchdog
        logger.debug("App healthy. Pet the watchdog.");
        try {
            ShellManager shellManager = new ShellManager(new String[]{"systemd-notify", "WATCHDOG=1"});
            shellManager.getProcess().waitFor();
            shellManager.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}