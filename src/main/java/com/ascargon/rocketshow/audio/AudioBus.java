package com.ascargon.rocketshow.audio;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * An audio bus containing a number of channels.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class AudioBus {

	private String name;

	private int channels = 2;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getChannels() {
		return channels;
	}

	public void setChannels(int channels) {
		this.channels = channels;
	}

}
