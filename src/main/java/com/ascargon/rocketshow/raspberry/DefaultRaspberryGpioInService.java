package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfigBuilder;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DefaultRaspberryGpioInService implements RaspberryGpioInService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioInService.class);

    private Context pi4j = null;
    private List<DigitalInput> digitalInputList = new ArrayList<>();

    public DefaultRaspberryGpioInService(SettingsService settingsService, ActionExecutionService actionExecutionService, Pi4jService pi4jService) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        pi4j = pi4jService.getContext();

        // Currently not working on the Pi CM5. See https://github.com/Pi4J/pi4j-example-minimal/issues/13
//        initializeInput(settingsService, actionExecutionService);
    }

    private void initializeInput(SettingsService settingsService, ActionExecutionService actionExecutionService) {
        logger.info("Initialize GPIO listener...");

        // Add a button for each configured control
        for (ActionTriggerRaspberryGpio actionTriggerRaspberryGpio : settingsService.getSettings().getActionTriggerRaspberryGpioList()) {
            logger.info("Start GPIO listener for BCM " + actionTriggerRaspberryGpio.getPinId() + " ID");

            DigitalInputConfigBuilder buttonConfig = DigitalInput.newConfigBuilder(pi4j)
                    .id("io_" + UUID.randomUUID())
                    .name("Press button")
                    .address(actionTriggerRaspberryGpio.getPinId())
                    .pull(PullResistance.PULL_UP)
                    .debounce(settingsService.getSettings().getRaspberryGpioDebounceMillis() * 1000L * 0L + 3000L);
            DigitalInput digitalInput = pi4j.create(buttonConfig);
            digitalInput.addListener(event -> {
                logger.debug("Input low from GPIO BCM " + event.source().address() + " ID recognized");
                System.out.println("Input low from GPIO BCM " + event.source().address() + " ID recognized");
                if (event.state() == DigitalState.LOW) {
                    logger.debug("Input low from GPIO BCM " + event.source().address() + " ID recognized");
                    System.out.println("Input low from GPIO BCM " + event.source().address() + " ID recognized");
                    try {
                        actionExecutionService.executeFromTrigger(actionTriggerRaspberryGpio);
                    } catch (Exception e) {
                        logger.error("Could not executeFromTrigger action from Raspberry GPIO", e);
                    }
                }
            });
            System.out.println("Initial state: " + digitalInput.state() + " for id " + digitalInput.address());
            digitalInputList.add(digitalInput);
        }
    }

}
