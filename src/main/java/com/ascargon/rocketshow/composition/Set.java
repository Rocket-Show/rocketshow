package com.ascargon.rocketshow.composition;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class Set {

    private String name;
    private String notes;
    private List<SetComposition> setCompositionList = new ArrayList<>();

    // Return only the set-relevant information of the composition (to save it
    // to a file)
    @XmlElement(name = "composition")
    @XmlElementWrapper(name = "compositionList")
    @JsonProperty("compositionList")
    public List<SetComposition> getSetCompositionList() {
        return setCompositionList;
    }

    public void setSetCompositionList(List<SetComposition> setCompositionList) {
        this.setCompositionList = setCompositionList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

}
