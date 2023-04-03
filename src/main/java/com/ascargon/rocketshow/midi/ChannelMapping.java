package com.ascargon.rocketshow.midi;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ChannelMapping {

	private int channelFrom;
	private int channelTo;

	public int getChannelFrom() {
		return channelFrom;
	}
	
	public void setChannelFrom(int channelFrom) {
		this.channelFrom = channelFrom;
	}

	public int getChannelTo() {
		return channelTo;
	}

    @SuppressWarnings("unused")
	public void setChannelTo(int channelTo) {
		this.channelTo = channelTo;
	}
	
}
