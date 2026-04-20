package com.ascargon.rocketshow.session;

import com.ascargon.rocketshow.update.UpdateState;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class Session {

    private String currentSetName;
    private boolean autoSelectNextComposition = false;
    private UpdateState updateState;

}
