package com.ascargon.rocketshow.midi;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ChannelMapping {

	private int channelFrom;
	private int channelTo;
	
	@XmlElement
	public int getChannelFrom() {
		return channelFrom;
	}
	
	public void setChannelFrom(int channelFrom) {
		this.channelFrom = channelFrom;
	}
	
	@XmlElement
	public int getChannelTo() {
		return channelTo;
	}

    @SuppressWarnings("unused")
	public void setChannelTo(int channelTo) {
		this.channelTo = channelTo;
	}
	
}
