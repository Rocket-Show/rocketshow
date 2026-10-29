package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.health.HealthService;
import com.ascargon.rocketshow.health.HealthStatus;
import com.ascargon.rocketshow.health.HealthStatusSeverity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The health of the unit, as the watchdog and the updates see it.
 */
@Service
class HealthSelfTest implements SelfTestStep {

    static final String HEALTH = "health";

    private final HealthService healthService;

    HealthSelfTest(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(HEALTH);
    }

    @Override
    public void run(SelfTestContext context) {
        HealthStatus healthStatus = healthService.getHealthStatus();
        HealthStatusSeverity severity = healthStatus.getHealthStatusSeverity();

        String details = severity + (healthStatus.getReasons().isEmpty() ? "" : ": " + String.join("; ", healthStatus.getReasons()));

        if (severity == HealthStatusSeverity.OK || severity == HealthStatusSeverity.DEGRADED) {
            context.report(SelfTestCheck.passed(HEALTH, null, details));
        } else {
            context.report(SelfTestCheck.failed(HEALTH, null, details));
        }
    }

}
