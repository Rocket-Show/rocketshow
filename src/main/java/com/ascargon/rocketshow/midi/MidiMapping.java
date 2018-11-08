package com.ascargon.rocketshow.midi;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement
public class MidiMapping {

	private MidiMapping parent;

	private List<ChannelMapping> channelMap = new ArrayList<ChannelMapping>();
	
	private Integer channelOffset = 0;
	private Integer noteOffset = 0;

	// Completely ignore all parent's settings
	private Boolean overrideParent = false;

	@XmlTransient
	public MidiMapping getParent() {
		return parent;
	}

	public void setParent(MidiMapping parent) {
		this.parent = parent;
	}

	public Boolean isOverrideParent() {
		return overrideParent;
	}

    @SuppressWarnings("unused")
	public void setOverrideParent(Boolean overrideParent) {
		this.overrideParent = overrideParent;
	}

	public List<ChannelMapping> getChannelMap() {
		return channelMap;
	}

    @SuppressWarnings("unused")
	public void setChannelMap(List<ChannelMapping> channelMap) {
		this.channelMap = channelMap;
	}

	public Integer getChannelOffset() {
		return channelOffset;
	}

	public void setChannelOffset(Integer channelOffset) {
		this.channelOffset = channelOffset;
	}

	public Integer getNoteOffset() {
		return noteOffset;
	}

	public void setNoteOffset(Integer noteOffset) {
		this.noteOffset = noteOffset;
	}

}
