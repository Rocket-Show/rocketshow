package com.ascargon.rocketshow.raspberry;

import org.springframework.stereotype.Service;

@Service
public interface RaspberryGpioOutService {
    void executeAction(RaspberryGpioAction gpioAction);
}
