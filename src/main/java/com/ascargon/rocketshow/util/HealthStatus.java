package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

enum Severity {OK, DEGRADED, FAIL_RESTART_APP, FAIL_REBOOT_DEVICE}

@Getter
@Setter
public class HealthStatus {

    private Severity severity;

    private boolean audioOk = true;
    private boolean midiOk = true;
    private boolean dmxOk = true;

    private Long freeDiskSpacePercentage;
    private boolean freeDiskSpacePercentageOk = true;

    private Long freeMemory;
    private boolean memoryOk = true;

    private Long temperature;
    private boolean temperatureOk = true;

    private int recentErrorRate;
    private boolean recentErrorRateOk = true;

    private List<String> reasons;

    public String toFailureString() {
        StringBuilder sb = new StringBuilder("Health failure: ");

        if (!audioOk) sb.append("audio ");
        if (!midiOk) sb.append("midi ");
        if (!dmxOk) sb.append("dmx ");
        if (!freeDiskSpacePercentageOk) sb.append("disk ");
        if (!memoryOk) sb.append("memory ");
        if (!temperatureOk) sb.append("temperature ");
        if (!recentErrorRateOk) sb.append("errorRate ");

        if (reasons != null && !reasons.isEmpty()) {
            sb.append(" reasons=").append(reasons);
        }

        return sb.toString();
    }

}