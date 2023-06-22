package com.ascargon.rocketshow.util;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Release change notes for the app.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
class ChangeNote {

	private String version;
	private String changes;

	public ChangeNote() {
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getChanges() {
		return changes;
	}

	public void setChanges(String changes) {
		this.changes = changes;
	}

}
