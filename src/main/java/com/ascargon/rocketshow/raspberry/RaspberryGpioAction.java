package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RaspberryGpioAction extends Action {

    // A BCM GPIO pin ID (e.g. BCM 22 = pin 15)
    // See: https://www.pi4j.com/documentation/pin-numbering/
    private Integer pinId;

    private Boolean high;

    @Override
    public ActionType getType() {
        return ActionType.RASPBERRY_GPIO;
    }
}
