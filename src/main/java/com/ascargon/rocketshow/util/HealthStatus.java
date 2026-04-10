package com.ascargon.rocketshow.util;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

enum Severity {OK, DEGRADED, FAIL_RESTART_APP, FAIL_REBOOT_DEVICE}

@Getter
@Setter
public class HealthStatus {

    private Severity severity = Severity.OK;

    private Long freeDiskSpacePercentage;
    private Long freeMemory;
    private Double temperature;
    private int recentErrorRate;
    private String softwareVersion;
    private Date softwareDate;

    private List<String> reasons = new ArrayList<>();

    public String toFailureString() {
        StringBuilder sb = new StringBuilder("Health failure: ");

        if (reasons != null && !reasons.isEmpty()) {
            sb.append(" reasons=").append(reasons);
        }

        return sb.toString();
    }

}