package com.ascargon.rocketshow.update;

import com.ascargon.rocketshow.api.NotificationService;
import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.health.HealthStatusSeverity;
import com.ascargon.rocketshow.session.SessionService;
import com.ascargon.rocketshow.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DefaultUpdateService implements UpdateService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultUpdateService.class);

    private final NotificationService notificationService;
    private final SessionService sessionService;
    private final RebootService rebootService;
    private final RaucService raucService;
    private final HealthService healthService;
    private final VersionService versionService;

    public DefaultUpdateService(
            NotificationService notificationService,
            SessionService sessionService,
            RebootService rebootService,
            RaucService raucService,
            HealthService healthService,
            VersionService versionService
    ) {
        this.notificationService = notificationService;
        this.sessionService = sessionService;
        this.rebootService = rebootService;
        this.raucService = raucService;
        this.healthService = healthService;
        this.versionService = versionService;

        // Check, whether we can finish an ongoing update after reboot
        if (sessionService.getSession().getUpdateState() != null) {
            if (UpdateStep.REBOOTING.equals(sessionService.getSession().getUpdateState().getStep())) {
                // We booted into the updated slot for the first time
                try {
                    finishUpdate();
                } catch (Exception e) {
                    error("Could not finish update: " + e);
                }
            } else if (UpdateStep.FALLING_BACK.equals(sessionService.getSession().getUpdateState().getStep())) {
                // We booted back into the original slot after the update failed
                error("Update failed. Reverted original state.");
            }
        }
    }

    private void sendState(UpdateState updateState) throws Exception {
        sessionService.getSession().setUpdateState(updateState);
        sessionService.save();
        notificationService.notifyClients(updateState);
    }

    private void startUpdate(String currentVersion) throws Exception {
        UpdateState updateState = new UpdateState();
        updateState.setStep(UpdateStep.UPDATING);
        updateState.setUpdatingFromVersion(currentVersion);
        sendState(updateState);
    }

    private void error(String error) {
        logger.error(error);

        UpdateState updateState = new UpdateState();
        updateState.setStep(UpdateStep.FINISHED);
        updateState.setError(error);

        try {
            sendState(updateState);
        } catch (Exception e) {
            logger.error("Could not log update error", e);
        }
    }

    private void prepareReboot() throws Exception {
        UpdateState updateState = new UpdateState();
        updateState.setStep(UpdateStep.REBOOTING);
        sendState(updateState);
    }

    private void finishUpdate() throws Exception {
        // We booted into the updated slot for the first time.
        // Check, whether we are healthy and could upgrade the version or whether
        // we need to fall back

        HealthStatus healthStatus = healthService.getHealthStatus();

        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK ||
                healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.DEGRADED) {

            // Update successful
            raucService.markGood();

            UpdateState updateState = new UpdateState();
            updateState.setStep(UpdateStep.FINISHED);
            sendState(updateState);
        } else {
            // Update failed -> reboot and fallback to the old slot
            logger.error("Update failed. Health check failed: {}", healthStatus.toFailureString());
            UpdateState updateState = new UpdateState();
            updateState.setStep(UpdateStep.FALLING_BACK);
            sendState(updateState);
            rebootService.reboot();
        }
    }

    @Override
    public void update(boolean testBranch) throws Exception {
        logger.info("Updating system...");
        logger.info("Test branch: {}", testBranch);

        VersionInfo remoteVersionInfo = versionService.getRemoteVersionInfo(testBranch);
        String currentVersion = versionService.getCurrentVersionInfo().getVersion();
        String newVersion = remoteVersionInfo.getVersion();

        logger.info("Current version: {}", currentVersion);
        logger.info("New version: {}", newVersion);
        logger.info("Current RAUC slot: {}", raucService.getCurrentSlot());

        if (newVersion.equals(currentVersion)) {
            error("New version is equal to the currently installed version");
            return;
        }

        startUpdate(currentVersion);

        try {
            raucService.installBundle(versionService.getRemoteBaseUrl(testBranch) + "rauc-bundles/" + remoteVersionInfo.getRaucBundle());
        } catch (Exception e) {
            error("Unable to install RAUC bundle");
            return;
        }

        prepareReboot();

        rebootService.tryboot();
    }

}