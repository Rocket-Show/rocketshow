package com.ascargon.rocketshow.raspberry;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class RaspberryGpioAction {

	// A BCM GPIO pin ID (e.g. BCM 22 = pin 15)
	// See: https://www.pi4j.com/documentation/pin-numbering/
	private Integer pinId;

	private Boolean high;

}
