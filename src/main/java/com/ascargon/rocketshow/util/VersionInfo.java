package com.ascargon.rocketshow.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@XmlRootElement
@Getter
@Setter
public class VersionInfo {

	private String version;
	private Date date;
    private String raucBundle;
	private List<ChangeNote> changeNotes;

	public VersionInfo() {
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
