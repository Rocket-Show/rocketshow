package com.ascargon.rocketshow;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

/**
 * Defines an instrument played in the band.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class Instrument {

	final static Logger logger = Logger.getLogger(Instrument.class);

	private String uuid;
	
	// The name of the instrument
	private String name;

	@XmlElement
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement
	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

}
