package com.ascargon.rocketshow.util;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

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

@XmlRootElement
public class RaspberryGpio {

	private GpioController gpioController;

	private List<GpioPinDigitalInput> buttons = new ArrayList<GpioPinDigitalInput>();

	final static Logger logger = Logger.getLogger(RaspberryGpio.class);

	public RaspberryGpio(Manager manager) {
		if (!manager.getSettings().isEnableRaspberryGpio()) {
			return;
		}

		// Initialize the instance
		gpioController = GpioFactory.getInstance();

		// Add a button
		addButton(RaspiPin.GPIO_02);
	}

	private void addButton(Pin pin) {
		GpioPinDigitalInput button = gpioController.provisionDigitalInputPin(pin, PinPullResistance.PULL_DOWN);
		button.setShutdownOptions(true);

		button.addListener(new GpioPinListenerDigital() {
			@Override
			public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
				// display pin state on console
				System.out.println(" --> GPIO PIN STATE CHANGE: " + event.getPin() + " = " + event.getState());
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
