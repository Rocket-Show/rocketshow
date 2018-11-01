package com.ascargon.rocketshow.composition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.Manager;

@XmlRootElement
abstract public class File {

	public enum FileType {
		MIDI, AUDIO, VIDEO, UNKNOWN
	}

	public final static String MEDIA_PATH = "media/";

	private String name;

	private Manager manager;

	private boolean active = true;

	private long durationMillis;
	
	private boolean loop = false;

	public File() {
	}

	// Play offset
	private int offsetMillis = 0;

	@XmlElement
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement
	public int getOffsetMillis() {
		return offsetMillis;
	}

	public void setOffsetMillis(int offsetMillis) {
		this.offsetMillis = offsetMillis;
	}

	@XmlTransient
	public Manager getManager() {
		return manager;
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public FileType getType() {
		return FileType.UNKNOWN;
	}

	// For API consistency
	public void setType(FileType fileType) {
	}

	public boolean isLoop() {
		return loop;
	}

	public void setLoop(boolean loop) {
		this.loop = loop;
	}

}
