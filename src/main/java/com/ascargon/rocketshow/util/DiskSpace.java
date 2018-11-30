package com.ascargon.rocketshow.util;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Container for the used and available disc space.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class DiskSpace {

	private double usedMB = 0;
	private double availableMB = 0;

	public double getUsedMB() {
		return usedMB;
	}
	public void setUsedMB(double usedMB) {
		this.usedMB = usedMB;
	}

	public double getAvailableMB() {
		return availableMB;
	}
	public void setAvailableMB(double availableMB) {
		this.availableMB = availableMB;
	}

}
