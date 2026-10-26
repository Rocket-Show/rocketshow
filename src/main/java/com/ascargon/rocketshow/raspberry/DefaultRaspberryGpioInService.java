package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.RaspberryGpioChangedEvent;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.DeviceInformationService;
import com.ascargon.rocketshow.util.FactoryResetService;
import com.ascargon.rocketshow.util.RebootService;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalInputConfigBuilder;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DefaultRaspberryGpioInService implements RaspberryGpioInService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultRaspberryGpioInService.class);

    private final SettingsService settingsService;
    private final ActionExecutionService actionExecutionService;
    private final Pi4jService pi4jService;
    private final DeviceInformationService deviceInformationService;
    private final FactoryResetService factoryResetService;
    private final RebootService rebootService;

    private Context pi4j = null;
    private DigitalInput resetButton;
    private final List<DigitalInput> digitalInputList = new ArrayList<>();

    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    AtomicBoolean resetScheduled = new AtomicBoolean(false);
    AtomicBoolean resetTriggered = new AtomicBoolean(false);
    AtomicReference<ScheduledFuture<?>> resetFuture = new AtomicReference<>();

    public DefaultRaspberryGpioInService(
            SettingsService settingsService,
            ActionExecutionService actionExecutionService,
            Pi4jService pi4jService,
            DeviceInformationService deviceInformationService,
            FactoryResetService factoryResetService,
            RebootService rebootService
    ) {
        this.settingsService = settingsService;
        this.actionExecutionService = actionExecutionService;
        this.pi4jService = pi4jService;
        this.deviceInformationService = deviceInformationService;
        this.factoryResetService = factoryResetService;
        this.rebootService = rebootService;

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

        // Currently not working on the Pi CM5. See https://github.com/Pi4J/pi4j-example-minimal/issues/13
        initializeInput();
    }

    @EventListener
    public synchronized void raspberryGpioChanged(RaspberryGpioChangedEvent event) {
        // Saving the settings replaces them, so listen with the triggers of the new settings. The reset button stays.
        for (DigitalInput digitalInput : digitalInputList) {
            try {
                pi4j.shutdown(digitalInput);
            } catch (Exception e) {
                logger.error("Could not stop listening to GPIO BCM " + digitalInput.address() + " ID", e);
            }
        }

        digitalInputList.clear();

        initialize();
    }

    private void initializeResetButton() {
        if (resetButton == null && deviceInformationService.getDeviceInformation().isAvailable()) {
            // Ready to use version
            DigitalInputConfigBuilder buttonConfig = DigitalInput.newConfigBuilder(pi4j)
                    .id("io_" + UUID.randomUUID())
                    .name("Reset button")
                    .address(17)
                    .pull(PullResistance.PULL_UP)
                    .debounce(3000L);
            DigitalInput digitalInput = pi4j.create(buttonConfig);
            resetButton = digitalInput;

            digitalInput.addListener(event -> {
                if (event.state() == DigitalState.LOW) {
                    // Button pressed
                    if (resetScheduled.compareAndSet(false, true)) {
                        resetTriggered.set(false);

                        ScheduledFuture<?> future = scheduler.schedule(() -> {
                            // Only reset if button is still pressed after 3 seconds
                            if (digitalInput.state() == DigitalState.LOW && !resetTriggered.get()) {
                                resetTriggered.set(true);
                                logger.debug("Reset button held for 3+ seconds");
                                try {
                                    factoryResetService.reset();
                                    rebootService.reboot();
                                } catch (Exception e) {
                                    logger.error("Could not factory reset from button press", e);
                                }
                            }
                        }, 3, TimeUnit.SECONDS);

                        resetFuture.set(future);
                    }
                } else if (event.state() == DigitalState.HIGH) {
                    // Button released before timeout or after reset
                    ScheduledFuture<?> future = resetFuture.getAndSet(null);
                    if (future != null && !future.isDone()) {
                        future.cancel(false);
                    }

                    resetScheduled.set(false);
                    resetTriggered.set(false);
                }
            });
        }
    }

    private void initializeTrigger(ActionTriggerRaspberryGpio actionTriggerRaspberryGpio) {
        logger.info("Start GPIO listener for BCM " + actionTriggerRaspberryGpio.getPinId() + " ID");

        DigitalInputConfigBuilder buttonConfig = DigitalInput.newConfigBuilder(pi4j)
                .id("io_" + UUID.randomUUID())
                .name("Press button")
                .address(actionTriggerRaspberryGpio.getPinId())
                .pull(PullResistance.PULL_UP)
                .debounce(settingsService.getSettings().getRaspberryGpioDebounceMillis() * 1000L);
        DigitalInput digitalInput = pi4j.create(buttonConfig);
        digitalInput.addListener(event -> {
            if (event.state() == DigitalState.LOW) {
                logger.debug("Input low from GPIO BCM " + event.source().address() + " ID recognized");
                try {
                    actionExecutionService.executeFromTrigger(actionTriggerRaspberryGpio);
                } catch (Exception e) {
                    logger.error("Could not executeFromTrigger action from Raspberry GPIO", e);
                }
            }
        });
        logger.debug("Initial state: " + digitalInput.state() + " for GPIO BCM " + digitalInput.address() + " ID");
        digitalInputList.add(digitalInput);
    }

    private void initializeInput() {
        logger.info("Initialize GPIO listener...");

        // One pin, which can't be used, must not break the others
        try {
            initializeResetButton();
        } catch (Exception e) {
            logger.error("Could not initialize the reset button on GPIO BCM 17 ID", e);
        }

        // Add a button for each configured control
        for (ActionTriggerRaspberryGpio actionTriggerRaspberryGpio : settingsService.getSettings().getActionTriggerRaspberryGpioList()) {
            try {
                initializeTrigger(actionTriggerRaspberryGpio);
            } catch (Exception e) {
                logger.error("Could not initialize GPIO BCM " + actionTriggerRaspberryGpio.getPinId() + " ID", e);
            }
        }
    }

}
