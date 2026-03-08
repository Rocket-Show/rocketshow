package com.ascargon.rocketshow.session;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class Session {

    private String currentSetName;
    private boolean updateFinished = false;
    private boolean autoSelectNextComposition = false;

}
