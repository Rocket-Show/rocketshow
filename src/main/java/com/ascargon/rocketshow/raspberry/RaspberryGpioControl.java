package com.ascargon.rocketshow.raspberry;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.ascargon.rocketshow.util.ControlAction;

@XmlRootElement
public class RaspberryGpioControl extends ControlAction {

	// A GPIO pin
	private int pinId;

	@XmlElement
	public int getPinId() {
		return pinId;
	}

	public void setPinId(int pinId) {
		this.pinId = pinId;
	}

}
