package com.ascargon.rocketshow.util;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

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

	@XmlElement
	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@XmlElement
	public String getChanges() {
		return changes;
	}

	public void setChanges(String changes) {
		this.changes = changes;
	}

}
