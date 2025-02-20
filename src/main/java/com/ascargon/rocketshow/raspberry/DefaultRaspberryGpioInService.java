package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ControlActionExecutionService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfigBuilder;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultRaspberryGpioInService implements RaspberryGpioInService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioInService.class);

    private Context pi4j = null;

    public DefaultRaspberryGpioInService(SettingsService settingsService, ControlActionExecutionService controlActionExecutionService, Pi4jService pi4jService) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        pi4j = pi4jService.getContext();

        initializeInput(settingsService, controlActionExecutionService);
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

}
