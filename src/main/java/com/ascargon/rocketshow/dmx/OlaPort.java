package com.ascargon.rocketshow.dmx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A response port we got from a OLA port list query.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OlaPort {
	private String device;
	private String id;
	private boolean isOutput;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDevice() {
		return device;
	}

	public void setDevice(String device) {
		this.device = device;
	}

	@JsonProperty("is_output")
	public boolean isOutput() {
		return isOutput;
	}

	public void setOutput(boolean isOutput) {
		this.isOutput = isOutput;
	}
}
