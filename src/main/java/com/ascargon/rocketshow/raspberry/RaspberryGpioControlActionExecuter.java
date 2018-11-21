package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.ControlActionExecutionService;
import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class RaspberryGpioControlActionExecuter {

    private GpioController gpioController;

    private final static Logger logger = LoggerFactory.getLogger(RaspberryGpioControlActionExecuter.class);

    public RaspberryGpioControlActionExecuter(SettingsService settingsService, ControlActionExecutionService controlActionExecutionService, PlayerService playerService) {
        if (!settingsService.getSettings().isEnableRaspberryGpio()) {
            return;
        }

        // Initialize the instance
        gpioController = GpioFactory.getInstance();

        // Add a button for each configured control
        for (RaspberryGpioControl raspberryGpioControl : settingsService.getSettings().getRaspberryGpioControlList()) {
            GpioPinDigitalInput button = gpioController.provisionDigitalInputPin(
                    getPinFromId(raspberryGpioControl.getPinId()), PinPullResistance.PULL_DOWN);

            button.setShutdownOptions(true);

            GpioPinListenerDigital listener = event -> {
                if (event.getState().isHigh()) {
                    logger.debug("Input high from GPIO " + event.getPin() + " recognized");

                    try {
                        controlActionExecutionService.execute(raspberryGpioControl);
                    } catch (Exception e) {
                        logger.error("Could not execute action from Raspberry GPIO", e);
                    }
                }
            };

            // Add the same listener for all pins, because a no-class-def found
            // error will raise, when
            // added
            button.addListener(listener);

            // TODO Make debounce time configurable
            button.setDebounce(500);
        }
    }

    // RaspiPin.getPinByAddress does not work ("read error: no device found")
    private Pin getPinFromId(int pinId) {
        switch (pinId) {
            case 0:
                return RaspiPin.GPIO_00;
            case 1:
                return RaspiPin.GPIO_01;
            case 2:
                return RaspiPin.GPIO_02;
            case 3:
                return RaspiPin.GPIO_03;
            case 4:
                return RaspiPin.GPIO_04;
            case 5:
                return RaspiPin.GPIO_05;
            case 6:
                return RaspiPin.GPIO_06;
            case 7:
                return RaspiPin.GPIO_07;
            case 10:
                return RaspiPin.GPIO_10;
            case 11:
                return RaspiPin.GPIO_11;
            case 12:
                return RaspiPin.GPIO_12;
            case 13:
                return RaspiPin.GPIO_13;
            case 14:
                return RaspiPin.GPIO_14;
            case 15:
                return RaspiPin.GPIO_15;
            case 16:
                return RaspiPin.GPIO_16;
            case 17:
                return RaspiPin.GPIO_17;
            case 18:
                return RaspiPin.GPIO_18;
            case 19:
                return RaspiPin.GPIO_19;
            case 20:
                return RaspiPin.GPIO_20;
            case 21:
                return RaspiPin.GPIO_21;
            case 22:
                return RaspiPin.GPIO_22;
            case 23:
                return RaspiPin.GPIO_23;
            case 24:
                return RaspiPin.GPIO_24;
            case 25:
                return RaspiPin.GPIO_25;
            case 26:
                return RaspiPin.GPIO_26;
            case 27:
                return RaspiPin.GPIO_27;
            case 28:
                return RaspiPin.GPIO_28;
            case 29:
                return RaspiPin.GPIO_29;
        }

        return null;
    }

    public void close() {
        if (gpioController != null) {
            gpioController.shutdown();
        }
    }

}
