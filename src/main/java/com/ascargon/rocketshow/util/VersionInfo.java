package com.ascargon.rocketshow.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class VersionInfo {

	private String version;
	private Date date;
	private List<ChangeNote> changeNotes;

	public VersionInfo() {
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	@XmlElement(name = "changeNote")
	@XmlElementWrapper(name = "changeNoteList")
    @JsonProperty("changeNoteList")
	@SuppressWarnings("unused")
	public List<ChangeNote> getChangeNotes() {
		return changeNotes;
	}

	@SuppressWarnings("unused")
	public void setChangeNotes(List<ChangeNote> changeNotes) {
		this.changeNotes = changeNotes;
	}

}
