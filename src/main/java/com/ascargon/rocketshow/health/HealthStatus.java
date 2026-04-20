package com.ascargon.rocketshow.health;

import com.ascargon.rocketshow.util.DeviceInformation;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class HealthStatus {

    private HealthStatusSeverity healthStatusSeverity = HealthStatusSeverity.OK;

    private DeviceInformation deviceInformation;
    private Long freeDiskSpacePercentage;
    private Long freeMemory;
    private Double temperature;
    private int recentErrorRate;
    private String softwareVersion;
    private Date softwareDate;
    private String raucSlot;

    private List<String> reasons = new ArrayList<>();

    public String toFailureString() {
        StringBuilder sb = new StringBuilder("Health failure: ");

        if (reasons != null && !reasons.isEmpty()) {
            sb.append(" reasons=").append(reasons);
        }

        return sb.toString();
    }

}