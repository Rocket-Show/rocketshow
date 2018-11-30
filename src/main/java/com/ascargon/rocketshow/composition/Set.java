package com.ascargon.rocketshow.composition;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@XmlRootElement
public class Set {

    private final static Logger logger = LoggerFactory.getLogger(Set.class);

    private String name;
    private String notes;
    private final List<SetComposition> setCompositionList = new ArrayList<>();

    // Return only the set-relevant information of the composition (to save it
    // to a file)
    @XmlElement(name = "composition")
    @XmlElementWrapper(name = "compositionList")
    public List<SetComposition> getSetCompositionList() {
        return setCompositionList;
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
