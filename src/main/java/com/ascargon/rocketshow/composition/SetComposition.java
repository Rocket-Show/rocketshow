package com.ascargon.rocketshow.composition;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * A composition inside a set containing less information than a "real"
 * composition.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class SetComposition {

	private String name;
	private long durationMillis;
	private boolean autoStartNextComposition = false;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public void setDurationMillis(long durationMillis) {
		this.durationMillis = durationMillis;
	}

	public boolean isAutoStartNextComposition() {
		return autoStartNextComposition;
	}

	public void setAutoStartNextComposition(boolean autoStartNextComposition) {
		this.autoStartNextComposition = autoStartNextComposition;
	}

}
