package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.util.ControlAction;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaspberryGpioControl extends ControlAction {

    // A BCM GPIO pin ID (e.g. BCM 22 = pin 15)
    // See: https://www.pi4j.com/documentation/pin-numbering/
    private Integer pinId;

}
