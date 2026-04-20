package com.ascargon.rocketshow.update;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateState {

    private boolean isUpdating;
    private String updatingFromVersion;
    private double progressPercentage;
    private String progressMessage;
    private String error;
    private boolean isRebooting;

}
