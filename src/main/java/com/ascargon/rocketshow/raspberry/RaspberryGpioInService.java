package com.ascargon.rocketshow.raspberry;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface RaspberryGpioInService {

    // Pins of the unit which are never switched, e.g. the reset button
    List<Integer> getReservedPins();

    // Don't execute the GPIO triggers while suspended (e.g. during the self-test). The reset button stays.
    void setTriggersSuspended(boolean suspended);

    // Whether the reset button is pressed now, null if the unit listens to no reset button
    Boolean isResetButtonPressed();

}
