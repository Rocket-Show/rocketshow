package com.ascargon.rocketshow.selftest;

import com.ascargon.rocketshow.raspberry.ActionTriggerRaspberryGpio;
import com.ascargon.rocketshow.raspberry.DefaultRaspberryGpioInService;
import com.ascargon.rocketshow.raspberry.DefaultRaspberryGpioOutService;
import com.ascargon.rocketshow.raspberry.Pi4jService;
import com.ascargon.rocketshow.settings.Settings;
import com.ascargon.rocketshow.settings.SettingsService;
import com.ascargon.rocketshow.util.ActionExecutionService;
import com.ascargon.rocketshow.util.DeviceInformation;
import com.ascargon.rocketshow.util.DeviceInformationService;
import com.ascargon.rocketshow.util.FactoryResetService;
import com.ascargon.rocketshow.util.RebootService;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputConfig;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.plugin.mock.platform.MockPlatform;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInput;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInputProvider;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalOutputProviderImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The GPIO checks of the self-test on simulated pins, with simulated loopback cables between them.
 */
class GpioSelfTestTest {

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private final Pi4jService pi4jService = mock(Pi4jService.class);
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
    private final DeviceInformation deviceInformation = new DeviceInformation();

    // The cables: output pin to the pin of the input, inverted if negative
    private final Map<Integer, Integer> cables = new ConcurrentHashMap<>();
    // Every state an output was switched to, by pin
    private final Map<Integer, List<DigitalState>> switched = new ConcurrentHashMap<>();

    private Context pi4j;
    private DefaultRaspberryGpioOutService outService;
    private DefaultRaspberryGpioInService inService;
    private GpioSelfTest gpioSelfTest;

    // Outputs switching the inputs they are connected to
    private class CabledOutputProvider extends MockDigitalOutputProviderImpl {

        @Override
        public DigitalOutput create(DigitalOutputConfig config) {
            DigitalOutput output = super.create(config);
            output.addListener(event -> {
                int pin = config.address();
                switched.computeIfAbsent(pin, key -> new CopyOnWriteArrayList<>()).add(event.state());
                Integer inputPin = cables.get(pin);
                if (inputPin != null) {
                    boolean high = event.state() == DigitalState.HIGH;
                    input(Math.abs(inputPin)).ifPresent(input -> input.mockState(high == inputPin > 0 ? DigitalState.HIGH : DigitalState.LOW));
                }
            });
            return output;
        }

    }

    @BeforeEach
    void setUp() {
        pi4j = Pi4J.newContextBuilder()
                .add(new MockPlatform())
                .add(MockDigitalInputProvider.newInstance(), new CabledOutputProvider())
                .build();

        settings.setEnableRaspberryGpio(true);
        when(settingsService.getSettings()).thenReturn(settings);
        when(pi4jService.getContext()).thenReturn(pi4j);
        when(deviceInformationService.getDeviceInformation()).thenReturn(deviceInformation);
    }

    @AfterEach
    void tearDown() {
        pi4j.shutdown();
    }

    private Optional<MockDigitalInput> input(int address) {
        return pi4j.registry().all().values().stream()
                .filter(io -> io instanceof MockDigitalInput input && input.address().intValue() == address)
                .map(io -> (MockDigitalInput) io)
                .findFirst();
    }

    private void startUnit() {
        outService = new DefaultRaspberryGpioOutService(settingsService, pi4jService);
        inService = new DefaultRaspberryGpioInService(settingsService, actionExecutionService, pi4jService, deviceInformationService, mock(FactoryResetService.class), mock(RebootService.class));
        gpioSelfTest = new GpioSelfTest(settingsService, outService, inService, pi4jService);
        gpioSelfTest.halfCycleMillis = 10;
    }

    private static SelfTestRequest loopbacks(SelfTestRequest.GpioLoopback... gpioLoopbacks) {
        SelfTestRequest request = new SelfTestRequest();
        request.getGpioLoopback().addAll(List.of(gpioLoopbacks));
        return request;
    }

    private static SelfTestRequest.GpioLoopback cable(int out, int in) {
        return new SelfTestRequest.GpioLoopback(out, in);
    }

    private SelfTestContext runSelfTest(SelfTestRequest request) throws Exception {
        SelfTestContext context = new SelfTestContext(request, () -> false);
        gpioSelfTest.validate(request);
        gpioSelfTest.prepare();
        try {
            gpioSelfTest.run(context);
        } finally {
            gpioSelfTest.restore();
        }
        return context;
    }

    private static List<SelfTestCheck> checks(SelfTestContext context, String name) {
        return context.getChecks().stream().filter(check -> check.getName().equals(name)).toList();
    }

    private static SelfTestCheck check(SelfTestContext context, String name) {
        List<SelfTestCheck> checks = checks(context, name);
        assertEquals(1, checks.size(), checks.toString());
        return checks.getFirst();
    }

    @Test
    void switchesTheOutputsOfTheSettingsAndPutsThemBack() throws Exception {
        settings.getRaspberryGpioOutputPinBcmList().addAll(List.of(23, 24));
        startUnit();
        // E.g. a relay an action switched on
        outService.setPinState(24, true);

        SelfTestCheck check = check(runSelfTest(new SelfTestRequest()), GpioSelfTest.GPIO_OUT);

        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertEquals("GPIO 23, 24", check.getTarget());
        assertEquals("Switched high and low 5 times", check.getDetails());
        assertEquals(5, switched.get(23).stream().filter(Predicate.isEqual(DigitalState.HIGH)).count());
        assertEquals(false, outService.getPinState(23));
        assertEquals(true, outService.getPinState(24));
    }

    @Test
    void neverSwitchesTheReservedPinsOrTheTriggers() throws Exception {
        deviceInformation.setAvailable(true);
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(22);
        settings.getActionTriggerRaspberryGpioList().add(trigger);
        settings.getRaspberryGpioOutputPinBcmList().addAll(List.of(17, 22, 23));
        startUnit();

        SelfTestCheck check = check(runSelfTest(new SelfTestRequest()), GpioSelfTest.GPIO_OUT);

        assertEquals("GPIO 23", check.getTarget());
        assertTrue(!switched.containsKey(17) && !switched.containsKey(22), switched.toString());
    }

    @Test
    void theOutputsAreSkippedWithoutGpio() throws Exception {
        settings.setEnableRaspberryGpio(false);
        settings.getRaspberryGpioOutputPinBcmList().add(23);
        startUnit();

        SelfTestCheck check = check(runSelfTest(new SelfTestRequest()), GpioSelfTest.GPIO_OUT);

        assertEquals(SelfTestOutcome.SKIPPED, check.getResult());
        assertEquals("GPIO is disabled in the settings", check.getDetails());
    }

    @Test
    void theOutputsAreSkippedWithoutOutputsInTheSettings() throws Exception {
        startUnit();

        SelfTestContext context = runSelfTest(new SelfTestRequest());

        assertEquals("No GPIO outputs in the settings", check(context, GpioSelfTest.GPIO_OUT).getDetails());
        assertEquals("No GPIO loopback in the request", check(context, GpioSelfTest.GPIO_LOOPBACK).getDetails());
    }

    @Test
    void theInputFollowsTheOutputThroughALoopbackCable() throws Exception {
        startUnit();
        cables.put(5, 6);

        SelfTestCheck check = check(runSelfTest(loopbacks(cable(5, 6))), GpioSelfTest.GPIO_LOOPBACK);

        assertEquals(SelfTestOutcome.PASSED, check.getResult(), check.getDetails());
        assertEquals("GPIO 5 → GPIO 6", check.getTarget());
        assertEquals("The input followed the output 3 times", check.getDetails());
        // The pins are released again
        assertTrue(pi4j.registry().all().keySet().stream().noneMatch(id -> id.startsWith("selftest")), pi4j.registry().all().keySet().toString());
    }

    @Test
    void theLoopbackIsSkippedWithoutCable() throws Exception {
        startUnit();

        SelfTestCheck check = check(runSelfTest(loopbacks(cable(5, 6))), GpioSelfTest.GPIO_LOOPBACK);

        assertEquals(SelfTestOutcome.SKIPPED, check.getResult());
        assertEquals("The input stays low, no loopback cable", check.getDetails());
    }

    @Test
    void failsWhenTheInputDoesNotFollow() throws Exception {
        startUnit();
        cables.put(5, -6);

        SelfTestCheck check = check(runSelfTest(loopbacks(cable(5, 6))), GpioSelfTest.GPIO_LOOPBACK);

        assertEquals(SelfTestOutcome.FAILED, check.getResult());
        assertEquals("The input was low while the output was high", check.getDetails());
    }

    @Test
    void checksEveryLoopback() throws Exception {
        startUnit();
        cables.put(5, 6);

        List<SelfTestCheck> checks = checks(runSelfTest(loopbacks(cable(5, 6), cable(12, 13))), GpioSelfTest.GPIO_LOOPBACK);

        assertEquals(List.of(SelfTestOutcome.PASSED, SelfTestOutcome.SKIPPED), checks.stream().map(SelfTestCheck::getResult).toList());
    }

    @Test
    void switchesAnOutputOfTheSettingsForALoopbackLikeAnAction() throws Exception {
        settings.getRaspberryGpioOutputPinBcmList().add(23);
        startUnit();
        cables.put(23, 6);

        SelfTestContext context = runSelfTest(loopbacks(cable(23, 6)));

        assertEquals(SelfTestOutcome.PASSED, check(context, GpioSelfTest.GPIO_LOOPBACK).getResult());
        assertEquals(false, outService.getPinState(23));
    }

    @Test
    void theTriggersDoNotFireDuringTheSelfTest() throws Exception {
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(22);
        settings.getActionTriggerRaspberryGpioList().add(trigger);
        startUnit();
        input(22).orElseThrow().mockState(DigitalState.HIGH);

        gpioSelfTest.prepare();
        input(22).orElseThrow().mockState(DigitalState.LOW);
        verify(actionExecutionService, never()).executeFromTrigger(trigger);

        gpioSelfTest.restore();
        input(22).orElseThrow().mockState(DigitalState.HIGH);
        input(22).orElseThrow().mockState(DigitalState.LOW);
        verify(actionExecutionService).executeFromTrigger(trigger);
    }

    private void assertRefused(String reason, SelfTestRequest request) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> gpioSelfTest.validate(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(reason, exception.getReason());
    }

    @Test
    void refusesLoopbacksWhichCouldDoHarm() {
        deviceInformation.setAvailable(true);
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(22);
        settings.getActionTriggerRaspberryGpioList().add(trigger);
        settings.getRaspberryGpioOutputPinBcmList().add(23);
        startUnit();

        assertRefused("GPIO 17 is reserved, e.g. for the reset button", loopbacks(cable(17, 6)));
        assertRefused("GPIO 17 is reserved, e.g. for the reset button", loopbacks(cable(5, 17)));
        assertRefused("GPIO 22 is a GPIO trigger in the settings, which may be wired to a button", loopbacks(cable(22, 6)));
        assertRefused("GPIO 22 is a GPIO trigger in the settings", loopbacks(cable(5, 22)));
        assertRefused("GPIO 23 is an output in the settings", loopbacks(cable(5, 23)));
        assertRefused("GPIO 5 is used by more than one loopback", loopbacks(cable(5, 5)));
        assertRefused("GPIO 6 is used by more than one loopback", loopbacks(cable(5, 6), cable(12, 6)));
        assertRefused("A GPIO loopback needs the pin of its output and of its input", loopbacks(new SelfTestRequest.GpioLoopback(5, null)));
    }

    @Test
    void theResetButtonIsOnlyReservedOnReadyToUseUnits() {
        startUnit();

        gpioSelfTest.validate(loopbacks(cable(17, 6)));
    }

}
