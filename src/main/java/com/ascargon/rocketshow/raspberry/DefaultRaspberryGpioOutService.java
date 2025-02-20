package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.SettingsService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultRaspberryGpioOutService implements RaspberryGpioOutService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioOutService.class);

    private Context pi4j;
    private List<DigitalOutput> digitalOutputList = new ArrayList<>();

    public DefaultRaspberryGpioOutService(SettingsService settingsService, Pi4jService pi4jService) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        pi4j = pi4jService.getContext();

        initializeOutput(settingsService);
    }

    private void initializeOutput(SettingsService settingsService) {
        // Add a input for each configured input
        // TODO
//        DigitalOutput digitalOutput = pi4j.digitalOutput().create(bcmPinId);
//        digitalOutputList.add(digitalOutput);
    }

//    @Override
//    public void setPinState(int bcmPinId, boolean active) {
//        for (DigitalOutput digitalOutput : digitalOutputList) {
//            if (digitalOutput.getAddress().equals(bcmPinId)) {
//                if (active) {
//                    digitalOutput.high();
//                } else {
//                    digitalOutput.low();
//                }
//                break;
//            }
//        }
//    }

    @Override
    public void executeAction(RaspberryGpioAction gpioAction) {
        // TODO aaaa
    }

}
