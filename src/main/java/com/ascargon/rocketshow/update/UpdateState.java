package com.ascargon.rocketshow.update;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;


@XmlRootElement
@Getter
@Setter
public class UpdateState {

    private UpdateStep step;
    private String updatingFromVersion;
    private double progressPercentage;
    private String progressMessage;
    private String error;

}
