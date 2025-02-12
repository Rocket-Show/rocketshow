package com.ascargon.rocketshow.raspberry;

import jakarta.xml.bind.annotation.XmlRootElement;

import com.ascargon.rocketshow.util.ControlAction;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class RaspberryGpioControl extends ControlAction {

	// A BCM GPIO pin ID (e.g. BCM 22 = pin 15)
	// See: https://www.pi4j.com/documentation/pin-numbering/
	private int pinId;

}
