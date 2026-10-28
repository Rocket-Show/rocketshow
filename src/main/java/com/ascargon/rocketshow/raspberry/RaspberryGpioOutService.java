package com.ascargon.rocketshow.raspberry;

import org.springframework.stereotype.Service;

@Service
public interface RaspberryGpioOutService {
    void executeAction(ActionRaspberryGpio gpioAction);

    void setAllLow();

    // Switches an output of the settings. False, if the pin isn't one.
    boolean setPinState(int bcmPinId, boolean high);

    // The state of an output of the settings: true for high, null if the pin isn't one
    Boolean getPinState(int bcmPinId);
}
