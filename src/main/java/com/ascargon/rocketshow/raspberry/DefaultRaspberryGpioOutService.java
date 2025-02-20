package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.SettingsService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultRaspberryGpioOutService implements RaspberryGpioOutService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioOutService.class);

    private Context pi4j;
    private final List<DigitalOutput> digitalOutputList = new ArrayList<>();

    public DefaultRaspberryGpioOutService(SettingsService settingsService, Pi4jService pi4jService) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        pi4j = pi4jService.getContext();

        initializeOutput(settingsService);
    }

    private void initializeOutput(SettingsService settingsService) {
        // Add a input for each configured input
        for (Integer outputAddress : settingsService.getSettings().getRaspberryGpioOutputPinBcmList()) {
            DigitalOutput digitalOutput = pi4j.digitalOutput().create(outputAddress);
            digitalOutputList.add(digitalOutput);
        }
    }

    private void setPinState(int bcmPinId, boolean high) {
        Optional<DigitalOutput> digitalOutputOptional = digitalOutputList.stream().filter(output -> output.getAddress().equals(bcmPinId)).findFirst();

        if (digitalOutputOptional.isEmpty()) {
            logger.warn("GPIO PIN BCM ID " + bcmPinId + " tried to be set by action, is not configured as output in the settings");
            return;
        }

        if (high) {
            logger.trace("Set GPIO PIN BCM ID " + bcmPinId + " to high");
            digitalOutputOptional.get().high();
        } else {
            logger.trace("Set GPIO PIN BCM ID " + bcmPinId + " to low");
            digitalOutputOptional.get().low();
        }
    }

    @Override
    public void executeAction(RaspberryGpioAction gpioAction) {
        setPinState(gpioAction.getPinId(), gpioAction.getHigh());
    }

}
