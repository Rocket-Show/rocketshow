package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.RaspberryGpioChangedEvent;
import com.ascargon.rocketshow.settings.SettingsService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DefaultRaspberryGpioOutService implements RaspberryGpioOutService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioOutService.class);

    private Context pi4j;
    private final List<DigitalOutput> digitalOutputList = new ArrayList<>();
    private final SettingsService settingsService;
    private final Pi4jService pi4jService;

    public DefaultRaspberryGpioOutService(SettingsService settingsService, Pi4jService pi4jService) {
        this.settingsService = settingsService;
        this.pi4jService = pi4jService;

        initialize();
    }

    private synchronized void initialize() {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        // The app has to start, even without GPIO (e.g. on a device model without supported GPIO). Otherwise,
        // GPIO can't even be disabled in the settings anymore.
        if (pi4j == null) {
            try {
                pi4j = pi4jService.getContext();
            } catch (Exception e) {
                logger.error("Could not initialize GPIO", e);
                return;
            }
        }

        initializeOutput();
    }

    @EventListener
    public synchronized void raspberryGpioChanged(RaspberryGpioChangedEvent event) {
        // Set up the outputs of the new settings
        for (DigitalOutput digitalOutput : digitalOutputList) {
            try {
                pi4j.shutdown(digitalOutput);
            } catch (Exception e) {
                logger.error("Could not release GPIO output BCM " + digitalOutput.address() + " ID", e);
            }
        }

        digitalOutputList.clear();

        initialize();
    }

    private void initializeOutput() {
        // Add an output for each configured output. One pin, which can't be used, must not break the others.
        for (Integer outputAddress : settingsService.getSettings().getRaspberryGpioOutputPinBcmList()) {
            try {
                DigitalOutput digitalOutput = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                        .id("out_" + outputAddress)
                        .address(outputAddress)
                        .initial(DigitalState.LOW));
                digitalOutputList.add(digitalOutput);
            } catch (Exception e) {
                logger.error("Could not initialize GPIO output BCM " + outputAddress + " ID", e);
            }
        }
    }

    private synchronized void setPinState(int bcmPinId, boolean high) {
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
    public void executeAction(ActionRaspberryGpio gpioAction) {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            logger.warn("Will not execute GPIO action, because GPIO is disabled in the settings");
            return;
        }

        setPinState(gpioAction.getPinId(), Boolean.TRUE.equals(gpioAction.getHigh()));
    }

    @Override
    public synchronized void setAllLow() {
        if (!settingsService.getSettings().getEnableRaspberryGpio()) {
            return;
        }

        for (DigitalOutput digitalOutput : digitalOutputList) {
            logger.trace("Set GPIO PIN BCM ID " + digitalOutput.getAddress() + " to low");
            digitalOutput.low();
        }
    }

}
