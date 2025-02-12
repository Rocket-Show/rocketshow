package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ControlActionExecutionService;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultRaspberryGpioService implements RaspberryGpioService {

    private Context pi4j;

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioService.class);

    private List<DigitalOutput> digitalOutputList = new ArrayList<>();

    public DefaultRaspberryGpioService(SettingsService settingsService, ControlActionExecutionService controlActionExecutionService) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        // Initialize the Pi4J instance
        pi4j = Pi4J.newAutoContext();

        initializeInput(settingsService, controlActionExecutionService);
        initializeOutput(settingsService);

    }

    private void initializeInput(SettingsService settingsService, ControlActionExecutionService controlActionExecutionService) {
        // Add a button for each configured control
        for (RaspberryGpioControl raspberryGpioControl : settingsService.getSettings().getRaspberryGpioControlList()) {
            DigitalInputConfigBuilder buttonConfig = DigitalInput.newConfigBuilder(pi4j)
                    .id("button")
                    .name("Press button")
                    .address(raspberryGpioControl.getPinId())
                    .pull(PullResistance.PULL_DOWN)
                    .debounce(settingsService.getSettings().getRaspberryGpioDebounceMillis() * 1000);
            DigitalInput digitalInput = pi4j.create(buttonConfig);
            digitalInput.addListener(event -> {
                if (event.state() == DigitalState.HIGH) {
                    logger.debug("Input high from GPIO BCM " + event.source().address() + " ID recognized");

                    try {
                        controlActionExecutionService.execute(raspberryGpioControl);
                    } catch (Exception e) {
                        logger.error("Could not execute action from Raspberry GPIO", e);
                    }
                }
            });
        }
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

    @PreDestroy
    public void close() {
        if (pi4j != null) {
            pi4j.shutdown();
        }
    }

}
