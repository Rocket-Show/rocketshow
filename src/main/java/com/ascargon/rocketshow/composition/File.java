package com.ascargon.rocketshow.composition;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import com.ascargon.rocketshow.Manager;

@XmlRootElement
abstract public class File implements PlayerLoadedListener {

	public enum FileType {
		MIDI, AUDIO, VIDEO, UNKNOWN
	}

	public final static String MEDIA_PATH = "media/";

	private String name;

	private Manager manager;
	private Composition composition;

	private boolean loading = false;
	private boolean loaded = false;

	private boolean active = true;

	private long durationMillis;
	
	private boolean loop = false;

	public File() {
	}

	// Play offset
	private int offsetMillis = 0;

	abstract public void load(long positionMillis) throws Exception;

	abstract public void close() throws Exception;

	abstract public void play() throws Exception;

	abstract public void pause() throws Exception;

	abstract public void resume() throws Exception;

	abstract public void stop() throws Exception;

	@Override
	public void playerLoaded() {
		loaded = true;
		loading = false;

		if (composition != null) {
			composition.playerLoaded();
		}
	}

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

	@XmlTransient
	public Composition getComposition() {
		return composition;
	}

	public void setComposition(Composition composition) {
		this.composition = composition;
	}

	@XmlTransient
	public boolean isLoaded() {
		return loaded;
	}

	public void setLoaded(boolean loaded) {
		this.loaded = loaded;
	}

	@XmlTransient
	public boolean isLoading() {
		return loading;
	}

	public void setLoading(boolean loading) {
		this.loading = loading;
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
