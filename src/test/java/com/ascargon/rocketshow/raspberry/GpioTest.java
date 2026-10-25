package com.ascargon.rocketshow.raspberry;

import com.ascargon.rocketshow.settings.RaspberryGpioChangedEvent;
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
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.plugin.mock.platform.MockPlatform;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInput;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInputProvider;
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalOutputProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Buttons and outputs on the GPIO pins of a Raspberry Pi, on simulated pins.
 */
class GpioTest {

    private final Settings settings = new Settings();
    private final SettingsService settingsService = mock(SettingsService.class);
    private final ActionExecutionService actionExecutionService = mock(ActionExecutionService.class);
    private final Pi4jService pi4jService = mock(Pi4jService.class);
    private final DeviceInformationService deviceInformationService = mock(DeviceInformationService.class);
    private final DeviceInformation deviceInformation = new DeviceInformation();
    private final FactoryResetService factoryResetService = mock(FactoryResetService.class);
    private final RebootService rebootService = mock(RebootService.class);
    private Context pi4j;

    @BeforeEach
    void setUp() {
        pi4j = Pi4J.newContextBuilder()
                .add(new MockPlatform())
                .add(MockDigitalInputProvider.newInstance(), MockDigitalOutputProvider.newInstance())
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

    private DefaultRaspberryGpioInService startInputs() {
        return new DefaultRaspberryGpioInService(settingsService, actionExecutionService, pi4jService, deviceInformationService, factoryResetService, rebootService);
    }

    private static ActionTriggerRaspberryGpio trigger(int pinId) {
        ActionTriggerRaspberryGpio trigger = new ActionTriggerRaspberryGpio();
        trigger.setPinId(pinId);
        return trigger;
    }

    // The buttons are connected to the ground, the pins pulled up
    private MockDigitalInput pin(int address) {
        List<MockDigitalInput> inputs = pi4j.registry().all().values().stream()
                .filter(io -> io instanceof MockDigitalInput input && input.address().intValue() == address)
                .map(io -> (MockDigitalInput) io)
                .toList();
        assertEquals(1, inputs.size(), "Inputs on BCM " + address);
        return inputs.getFirst();
    }

    private void press(int address) {
        pin(address).mockState(DigitalState.LOW);
    }

    private void release(int address) {
        pin(address).mockState(DigitalState.HIGH);
    }

    private DigitalState output(int address) {
        return pi4j.registry().all().values().stream()
                .filter(io -> io instanceof DigitalOutput output && output.address().intValue() == address)
                .map(io -> ((DigitalOutput) io).state())
                .findFirst().orElseThrow();
    }

    private static ActionRaspberryGpio setPin(int pinId, boolean high) {
        ActionRaspberryGpio action = new ActionRaspberryGpio();
        action.setPinId(pinId);
        action.setHigh(high);
        return action;
    }

    @Test
    void firesTheTriggerOfAButtonOnEachPress() throws Exception {
        ActionTriggerRaspberryGpio trigger = trigger(22);
        settings.getActionTriggerRaspberryGpioList().add(trigger);
        startInputs();

        release(22);
        press(22);
        verify(actionExecutionService).executeFromTrigger(trigger);

        release(22);
        verify(actionExecutionService).executeFromTrigger(trigger);

        press(22);
        verify(actionExecutionService, times(2)).executeFromTrigger(trigger);
    }

    @Test
    void firesTheTriggersOfTheOtherButtons() throws Exception {
        ActionTriggerRaspberryGpio first = trigger(22);
        ActionTriggerRaspberryGpio second = trigger(27);
        settings.getActionTriggerRaspberryGpioList().addAll(List.of(first, second));
        startInputs();

        release(27);
        press(27);

        verify(actionExecutionService).executeFromTrigger(second);
        verify(actionExecutionService, never()).executeFromTrigger(first);
    }

    @Test
    void listensToTheButtonsOfChangedSettings() throws Exception {
        ActionTriggerRaspberryGpio oldTrigger = trigger(22);
        settings.getActionTriggerRaspberryGpioList().add(oldTrigger);
        deviceInformation.setAvailable(true);
        DefaultRaspberryGpioInService inService = startInputs();

        // Saving the settings replaces them: the button on 22 got another action, a button on 27 has been added
        ActionTriggerRaspberryGpio changedTrigger = trigger(22);
        ActionTriggerRaspberryGpio newTrigger = trigger(27);
        settings.getActionTriggerRaspberryGpioList().clear();
        settings.getActionTriggerRaspberryGpioList().addAll(List.of(changedTrigger, newTrigger));
        inService.raspberryGpioChanged(new RaspberryGpioChangedEvent());

        release(22);
        press(22);
        release(27);
        press(27);

        verify(actionExecutionService).executeFromTrigger(changedTrigger);
        verify(actionExecutionService).executeFromTrigger(newTrigger);
        verify(actionExecutionService, never()).executeFromTrigger(oldTrigger);

        // Removed
        settings.getActionTriggerRaspberryGpioList().clear();
        inService.raspberryGpioChanged(new RaspberryGpioChangedEvent());
        assertEquals(List.of(17), pi4j.registry().all().values().stream().map(io -> ((MockDigitalInput) io).address().intValue()).toList());
    }

    @Test
    void usesTheOtherButtonsBesideAPinWhichCannotBeUsed() throws Exception {
        // 17 is the reset button
        deviceInformation.setAvailable(true);
        ActionTriggerRaspberryGpio taken = trigger(17);
        ActionTriggerRaspberryGpio free = trigger(22);
        settings.getActionTriggerRaspberryGpioList().addAll(List.of(taken, free));
        startInputs();

        release(22);
        press(22);

        verify(actionExecutionService).executeFromTrigger(free);
    }

    @Test
    void resetsTheUnitOnlyWhenTheButtonIsHeldFor3Seconds() throws Exception {
        // Only the ready to use units have the reset button
        deviceInformation.setAvailable(true);
        startInputs();

        // Pressed by accident
        release(17);
        press(17);
        Thread.sleep(1000);
        release(17);
        verify(factoryResetService, after(3000).never()).reset();

        press(17);
        verify(factoryResetService, timeout(4000)).reset();
        verify(rebootService, timeout(1000)).reboot();
    }

    @Test
    void hasNoResetButtonOnTheCommunityEdition() {
        startInputs();

        assertEquals(0, pi4j.registry().all().size());
    }

    @Test
    void setsTheConfiguredOutputs() {
        settings.getRaspberryGpioOutputPinBcmList().addAll(List.of(23, 24));
        DefaultRaspberryGpioOutService outService = new DefaultRaspberryGpioOutService(settingsService, pi4jService);

        outService.executeAction(setPin(23, true));
        assertEquals(DigitalState.HIGH, output(23));
        assertEquals(DigitalState.LOW, output(24));

        // Not configured as an output
        outService.executeAction(setPin(25, true));

        outService.executeAction(setPin(24, true));
        outService.setAllLow();
        assertEquals(DigitalState.LOW, output(23));
        assertEquals(DigitalState.LOW, output(24));
    }

    @Test
    void setsTheOutputsOfChangedSettings() {
        settings.getRaspberryGpioOutputPinBcmList().add(23);
        DefaultRaspberryGpioOutService outService = new DefaultRaspberryGpioOutService(settingsService, pi4jService);

        settings.getRaspberryGpioOutputPinBcmList().clear();
        settings.getRaspberryGpioOutputPinBcmList().add(24);
        outService.raspberryGpioChanged(new RaspberryGpioChangedEvent());
        outService.executeAction(setPin(24, true));

        assertEquals(DigitalState.HIGH, output(24));
        assertEquals(1, pi4j.registry().all().size());
    }

    @Test
    void usesNoPinsWhenDisabled() throws Exception {
        settings.setEnableRaspberryGpio(false);
        settings.getActionTriggerRaspberryGpioList().add(trigger(22));
        settings.getRaspberryGpioOutputPinBcmList().add(23);
        deviceInformation.setAvailable(true);

        startInputs();
        new DefaultRaspberryGpioOutService(settingsService, pi4jService).executeAction(setPin(23, true));

        verify(pi4jService, never()).getContext();
    }

    @Test
    void startsWithoutGpio() {
        // E.g. a device model, which Pi4J does not support
        when(pi4jService.getContext()).thenThrow(new IllegalStateException("No platform"));
        settings.getActionTriggerRaspberryGpioList().add(trigger(22));
        settings.getRaspberryGpioOutputPinBcmList().add(23);

        startInputs();
        new DefaultRaspberryGpioOutService(settingsService, pi4jService).executeAction(setPin(23, true));
    }

}
