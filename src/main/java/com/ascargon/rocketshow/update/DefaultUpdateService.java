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

        // Check, whether we can finish an ongoing update with this application start
        if (sessionService.getSession().getUpdateState() != null && sessionService.getSession().getUpdateState().isUpdating()) {
            finishUpdate();
        }
    }

    private void startUpdate(String currentVersion) throws Exception {
        UpdateState updateState = new UpdateState();
        updateState.setUpdating(true);
        updateState.setUpdatingFromVersion(currentVersion);

        sessionService.getSession().setUpdateState(updateState);
        sessionService.save();

        notificationService.notifyClients(updateState);
    }

    private void error(String error) throws Exception {
        logger.error(error);

        UpdateState updateState = new UpdateState();
        updateState.setUpdating(false);
        updateState.setError(error);

        sessionService.getSession().setUpdateState(updateState);
        sessionService.save();

        notificationService.notifyClients(updateState);
    }

    private void notifyReboot() throws Exception {
        UpdateState updateState = new UpdateState();
        updateState.setRebooting(true);
        notificationService.notifyClients(updateState);
    }

    private void finishUpdate() {
        // An update has started before the last application start
        // -> check, whether we are healthy and could upgrade the version

        HealthStatus healthStatus = healthService.getHealthStatus();

        if (healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.OK ||
                healthStatus.getHealthStatusSeverity() == HealthStatusSeverity.DEGRADED) {

        }

        // TODO
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
            raucService.installBundle(remoteVersionInfo.getRaucBundle());
        } catch (Exception e) {
            logger.error("Unable to install RAUC bundle", e);
            try {
                error(e.toString());
                return;
            } catch (Exception ex) {
                logger.error("could not notify clients about an error", e);
            }
        }

        notifyReboot();

        rebootService.tryboot();
    }

}