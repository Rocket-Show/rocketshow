package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.health.HealthStatusSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultWatchdogService implements WatchdogService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultWatchdogService.class);

    private final HealthService healthService;
    private final RebootService rebootService;
    private final DeviceInformationService deviceInformationService;

    // The reasons of the degraded health logged last. Logged once per change: the health lists the latest errors, so
    // logging it as an error on each check would add the last health to the next one, which grows with every check.
    private List<String> loggedDegradedReasons = List.of();

    public DefaultWatchdogService(HealthService healthService, RebootService rebootService, DeviceInformationService deviceInformationService, OperatingSystemInformationService operatingSystemInformationService) {
        this.healthService = healthService;
        this.rebootService = rebootService;
        this.deviceInformationService = deviceInformationService;

        if (!watchdogActive()) {
            logger.info("Watchdog is not activated");
            return;
        }

        try {
            notifySystemd("--ready", "--status=RocketShow ready");

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

        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.FAIL_REBOOT_DEVICE) {
            logger.error("System is unhealthy -> Reboot required");
            logger.error(healthStatus.toFailureString());

            try {
                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not reboot device", e);
            }
        }

        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.FAIL_RESTART_APP) {
            logger.error("System is unhealthy -> App restart required");
            logger.error(healthStatus.toFailureString());

            try {
                System.exit(1);
            } catch (Exception e) {
                logger.error("Error shutting down the app", e);
            }
        }

        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.DEGRADED) {
            if (!healthStatus.getReasons().equals(loggedDegradedReasons)) {
                logger.warn("System health is degraded: {}", healthStatus.getReasons());
                loggedDegradedReasons = healthStatus.getReasons();
            }
        } else if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK && !loggedDegradedReasons.isEmpty()) {
            logger.info("System health is OK again");
            loggedDegradedReasons = List.of();
        }

        // Everything fine, pet the watchdog
        logger.debug("App healthy. Pet the watchdog.");
        try {
            notifySystemd("WATCHDOG=1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void notifySystemd(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 1];
        command[0] = "systemd-notify";
        System.arraycopy(arguments, 0, command, 1, arguments.length);

        ShellManager shellManager = new ShellManager(command);
        shellManager.getProcess().waitFor();
        shellManager.close();
    }

}