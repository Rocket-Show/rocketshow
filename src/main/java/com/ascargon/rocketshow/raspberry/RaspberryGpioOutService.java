package com.ascargon.rocketshow.raspberry;

import org.springframework.stereotype.Service;

@Service
public interface RaspberryGpioOutService {
    void executeAction(ActionRaspberryGpio gpioAction);

    void setAllLow();
}
