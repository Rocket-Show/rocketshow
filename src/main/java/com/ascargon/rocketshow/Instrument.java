package com.ascargon.rocketshow;

import com.ascargon.rocketshow.composition.DefaultCompositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Defines an instrument played in the band.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class Instrument {

    private final static Logger logger = LoggerFactory.getLogger(DefaultCompositionService.class);

    private String uuid;

    // The name of the instrument
    private String name;

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

}
