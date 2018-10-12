package com.ascargon.rocketshow.raspberry;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class RaspberryGpioControlActionExecuter {

	private GpioController gpioController;

	private List<GpioPinDigitalInput> buttons = new ArrayList<GpioPinDigitalInput>();

	final static Logger logger = Logger.getLogger(RaspberryGpioControlActionExecuter.class);

	private Manager manager;
	
	public RaspberryGpioControlActionExecuter(Manager manager) {
		this.manager = manager;
		
		if (!manager.getSettings().isEnableRaspberryGpio()) {
			return;
		}

		// Initialize the instance
		gpioController = GpioFactory.getInstance();

		// Add a button for each configured control
		for(RaspberryGpioControl raspberryGpioControl : manager.getSettings().getRaspberryGpioControlList()) {
			addButton(raspberryGpioControl);
		}
		
		// TODO remove
		RaspberryGpioControl raspberryGpioControl = new RaspberryGpioControl();
		raspberryGpioControl.setPinId(15);
		addButton(raspberryGpioControl);
	}
	
	private Pin getPinFromId(int pinId) {
		switch (pinId) {
		case 2:
			return RaspiPin.GPIO_02;
		case 3:
			return RaspiPin.GPIO_03;
		case 15:
			return RaspiPin.GPIO_15;
		default:
			break;
		}
		
		return null;
	}

	private void addButton(RaspberryGpioControl raspberryGpioControl) {
		GpioPinDigitalInput button = gpioController.provisionDigitalInputPin(getPinFromId(raspberryGpioControl.getPinId()), PinPullResistance.PULL_DOWN);
		button.setShutdownOptions(true);

		button.addListener(new GpioPinListenerDigital() {
			@Override
			public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
				// display pin state on console
				logger.info(" --> GPIO PIN STATE CHANGE: " + event.getPin() + " = " + event.getState());
				
				if(event.getState().isHigh()) {
					logger.info("BUTTON PRESSED");
					
					try {
						manager.getControlActionExecuter().execute(raspberryGpioControl);
					} catch (Exception e) {
						logger.error("Could not execute action from Raspberry GPIO", e);
					}
				}
			}
		});

		buttons.add(button);
	}
	
	public void close() {
		if(gpioController != null) {
			gpioController.shutdown();
		}
	}

}
