package com.ascargon.rocketshow.composition;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@XmlRootElement
public class Set {

    private final static Logger logger = LogManager.getLogger(Set.class);

    private String name;
    private String notes;
    private List<SetComposition> setCompositionList = new ArrayList<>();

    // Return only the set-relevant information of the composition (to save it
    // to a file)
    @XmlElement(name = "composition")
    @XmlElementWrapper(name = "compositionList")
    public List<SetComposition> getSetCompositionList() {
        return setCompositionList;
    }

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

}
