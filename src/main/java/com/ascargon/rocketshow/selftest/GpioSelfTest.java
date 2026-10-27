package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.raspberry.ActionTriggerRaspberryGpio;
import com.ascargon.rocketshow.raspberry.Pi4jService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioInService;
import com.ascargon.rocketshow.raspberry.RaspberryGpioOutService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.pi4j.context.Context;
import com.pi4j.io.IO;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Switches the GPIO outputs of the settings (visible on a LED of a fixture), checks the loopbacks of the request (an
 * output connected to an input) and that the reset button isn't stuck. It never switches a reserved pin (e.g. the
 * reset button) or the pin of a GPIO trigger, which may be wired to a button. The GPIO triggers don't fire during the
 * whole self-test.
 */
@Service
class GpioSelfTest implements SelfTestStep {

    private final static Logger logger = LoggerFactory.getLogger(GpioSelfTest.class);

    static final String GPIO_OUT = "gpio-out";
    static final String GPIO_LOOPBACK = "gpio-loopback";
    static final String RESET_BUTTON = "reset-button";

    // Once per second, visible on a LED
    private static final int CYCLES = 5;

    private static final int LOOPBACK_CYCLES = 3;
    private static final long LOOPBACK_SETTLE_MILLIS = 20;

    private final SettingsService settingsService;
    private final RaspberryGpioOutService raspberryGpioOutService;
    private final RaspberryGpioInService raspberryGpioInService;
    private final Pi4jService pi4jService;

    // Package visible for the tests
    long halfCycleMillis = 500;

    // The outputs of the settings as they were before, to put them back afterwards
    private final Map<Integer, Boolean> outputStates = new LinkedHashMap<>();

    GpioSelfTest(SettingsService settingsService, RaspberryGpioOutService raspberryGpioOutService, RaspberryGpioInService raspberryGpioInService, Pi4jService pi4jService) {
        this.settingsService = settingsService;
        this.raspberryGpioOutService = raspberryGpioOutService;
        this.raspberryGpioInService = raspberryGpioInService;
        this.pi4jService = pi4jService;
    }

    @Override
    public List<String> getCheckNames() {
        return List.of(GPIO_OUT, GPIO_LOOPBACK, RESET_BUTTON);
    }

    private List<Integer> getTriggerPins() {
        return settingsService.getSettings().getActionTriggerRaspberryGpioList().stream()
                .map(ActionTriggerRaspberryGpio::getPinId)
                .toList();
    }

    private static String pins(List<Integer> pins) {
        return "GPIO " + pins.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    @Override
    public void validate(SelfTestRequest request) {
        List<Integer> reservedPins = raspberryGpioInService.getReservedPins();
        List<Integer> triggerPins = getTriggerPins();
        List<Integer> outputPins = settingsService.getSettings().getRaspberryGpioOutputPinBcmList();
        Set<Integer> usedPins = new HashSet<>();

        for (SelfTestRequest.GpioLoopback gpioLoopback : request.getGpioLoopback()) {
            Integer out = gpioLoopback.getOut();
            Integer in = gpioLoopback.getIn();

            if (out == null || in == null || out < 0 || in < 0) {
                throw badRequest("A GPIO loopback needs the pin of its output and of its input");
            }

            for (Integer pin : List.of(out, in)) {
                if (reservedPins.contains(pin)) {
                    throw badRequest("GPIO " + pin + " is reserved, e.g. for the reset button");
                }

                if (!usedPins.add(pin)) {
                    throw badRequest("GPIO " + pin + " is used by more than one loopback");
                }
            }

            if (triggerPins.contains(out)) {
                throw badRequest("GPIO " + out + " is a GPIO trigger in the settings, which may be wired to a button");
            }

            if (triggerPins.contains(in)) {
                throw badRequest("GPIO " + in + " is a GPIO trigger in the settings");
            }

            if (outputPins.contains(in)) {
                throw badRequest("GPIO " + in + " is an output in the settings");
            }
        }
    }

    @Override
    public synchronized void prepare() {
        raspberryGpioInService.setTriggersSuspended(true);

        outputStates.clear();
        for (Integer pin : settingsService.getSettings().getRaspberryGpioOutputPinBcmList()) {
            Boolean high = raspberryGpioOutService.getPinState(pin);
            if (high != null) {
                outputStates.put(pin, high);
            }
        }
    }

    @Override
    public synchronized void restore() {
        for (Map.Entry<Integer, Boolean> outputState : outputStates.entrySet()) {
            raspberryGpioOutService.setPinState(outputState.getKey(), outputState.getValue());
        }
        outputStates.clear();

        raspberryGpioInService.setTriggersSuspended(false);
    }

    @Override
    public void run(SelfTestContext context) throws Exception {
        checkOutputs(context);
        checkLoopbacks(context);
        checkResetButton(context);
    }

    // A button stuck pressed: it would reset the unit to the factory settings, or not work at all
    private void checkResetButton(SelfTestContext context) {
        List<Integer> reservedPins = raspberryGpioInService.getReservedPins();

        if (reservedPins.isEmpty()) {
            context.report(SelfTestCheck.skipped(RESET_BUTTON, null, "Only ready to use units have a reset button"));
            return;
        }

        String target = pins(reservedPins);
        Boolean pressed = raspberryGpioInService.isResetButtonPressed();

        if (pressed == null) {
            if (!Boolean.TRUE.equals(settingsService.getSettings().getEnableRaspberryGpio())) {
                context.report(SelfTestCheck.skipped(RESET_BUTTON, target, "GPIO is disabled in the settings, which disables the reset button as well"));
            } else {
                context.report(SelfTestCheck.failed(RESET_BUTTON, target, "The reset button could not be set up"));
            }
        } else if (pressed) {
            context.report(SelfTestCheck.failed(RESET_BUTTON, target, "Pressed: stuck, or someone holds it"));
        } else {
            context.report(SelfTestCheck.passed(RESET_BUTTON, target, "Released"));
        }
    }

    private void checkOutputs(SelfTestContext context) throws Exception {
        Settings settings = settingsService.getSettings();

        if (!Boolean.TRUE.equals(settings.getEnableRaspberryGpio())) {
            context.report(SelfTestCheck.skipped(GPIO_OUT, null, "GPIO is disabled in the settings"));
            return;
        }

        List<Integer> reservedPins = raspberryGpioInService.getReservedPins();
        List<Integer> triggerPins = getTriggerPins();
        List<Integer> outputPins = settings.getRaspberryGpioOutputPinBcmList().stream()
                .filter(pin -> !reservedPins.contains(pin) && !triggerPins.contains(pin))
                .distinct()
                .toList();

        if (outputPins.isEmpty()) {
            context.report(SelfTestCheck.skipped(GPIO_OUT, null, "No GPIO outputs in the settings"));
            return;
        }

        String target = pins(outputPins);

        List<Integer> notSetUp = outputPins.stream().filter(pin -> raspberryGpioOutService.getPinState(pin) == null).toList();
        if (!notSetUp.isEmpty()) {
            context.report(SelfTestCheck.failed(GPIO_OUT, target, pins(notSetUp) + " could not be set up as output"));
            return;
        }

        String wrong = null;

        for (int i = 0; i < CYCLES * 2; i++) {
            boolean high = i % 2 == 0;

            for (Integer pin : outputPins) {
                raspberryGpioOutService.setPinState(pin, high);
            }

            for (Integer pin : outputPins) {
                if (wrong == null && !Objects.equals(raspberryGpioOutService.getPinState(pin), high)) {
                    wrong = "GPIO " + pin + " read back " + (high ? "low" : "high") + " after switching it " + (high ? "high" : "low");
                }
            }

            if (!context.await(halfCycleMillis)) {
                context.report(SelfTestCheck.skipped(GPIO_OUT, target, SelfTestContext.STOPPED));
                return;
            }
        }

        if (wrong == null) {
            context.report(SelfTestCheck.passed(GPIO_OUT, target, "Switched high and low " + CYCLES + " times"));
        } else {
            context.report(SelfTestCheck.failed(GPIO_OUT, target, wrong));
        }
    }

    private void checkLoopbacks(SelfTestContext context) throws Exception {
        List<SelfTestRequest.GpioLoopback> gpioLoopbacks = context.getRequest().getGpioLoopback();

        if (gpioLoopbacks.isEmpty()) {
            context.report(SelfTestCheck.skipped(GPIO_LOOPBACK, null, "No GPIO loopback in the request"));
            return;
        }

        Context pi4j;
        try {
            pi4j = pi4jService.getContext();
        } catch (Exception e) {
            context.report(SelfTestCheck.failed(GPIO_LOOPBACK, null, "Could not initialize GPIO: " + e.getMessage()));
            return;
        }

        for (SelfTestRequest.GpioLoopback gpioLoopback : gpioLoopbacks) {
            String target = "GPIO " + gpioLoopback.getOut() + " → GPIO " + gpioLoopback.getIn();

            if (context.isStopped()) {
                context.report(SelfTestCheck.skipped(GPIO_LOOPBACK, target, SelfTestContext.STOPPED));
            } else {
                context.report(checkLoopback(pi4j, gpioLoopback.getOut(), gpioLoopback.getIn(), target));
            }
        }
    }

    private SelfTestCheck checkLoopback(Context pi4j, int out, int in, String target) throws Exception {
        List<IO<?, ?, ?>> ios = new ArrayList<>();

        try {
            DigitalInput input = pi4j.create(DigitalInput.newConfigBuilder(pi4j)
                    .id("selftest_in_" + in)
                    .address(in)
                    .pull(PullResistance.PULL_DOWN));
            ios.add(input);

            // An output of the settings switches like by an action, other pins only for the self-test
            DigitalOutput output = null;
            if (raspberryGpioOutService.getPinState(out) == null) {
                output = pi4j.create(DigitalOutput.newConfigBuilder(pi4j)
                        .id("selftest_out_" + out)
                        .address(out)
                        .initial(DigitalState.LOW));
                ios.add(output);
            }

            List<Boolean> inputStates = new ArrayList<>();
            String wrong = null;

            for (int i = 0; i < LOOPBACK_CYCLES * 2; i++) {
                boolean high = i % 2 == 0;

                if (output == null) {
                    raspberryGpioOutService.setPinState(out, high);
                } else {
                    output.state(high ? DigitalState.HIGH : DigitalState.LOW);
                }

                Thread.sleep(LOOPBACK_SETTLE_MILLIS);

                boolean inputHigh = input.isHigh();
                inputStates.add(inputHigh);

                if (wrong == null && inputHigh != high) {
                    wrong = "The input was " + (inputHigh ? "high" : "low") + " while the output was " + (high ? "high" : "low");
                }
            }

            if (output == null) {
                raspberryGpioOutService.setPinState(out, false);
            } else {
                output.low();
            }

            if (wrong == null) {
                return SelfTestCheck.passed(GPIO_LOOPBACK, target, "The input followed the output " + LOOPBACK_CYCLES + " times");
            }

            if (inputStates.stream().distinct().count() == 1) {
                return SelfTestCheck.skipped(GPIO_LOOPBACK, target, "The input stays " + (inputStates.getFirst() ? "high" : "low") + ", no loopback cable");
            }

            return SelfTestCheck.failed(GPIO_LOOPBACK, target, wrong);
        } finally {
            for (IO<?, ?, ?> io : ios) {
                try {
                    pi4j.shutdown(io);
                } catch (Exception e) {
                    logger.error("Could not release GPIO BCM {} ID after the self-test", io.id(), e);
                }
            }
        }
    }

}
