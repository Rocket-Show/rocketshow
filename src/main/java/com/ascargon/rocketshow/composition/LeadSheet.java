package com.ascargon.rocketshow.composition;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class LeadSheet {

    private String name;

    private String instrumentUuid;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstrumentUuid() {
        return instrumentUuid;
    }

    public void setInstrumentUuid(String instrumentUuid) {
        this.instrumentUuid = instrumentUuid;
    }

}
