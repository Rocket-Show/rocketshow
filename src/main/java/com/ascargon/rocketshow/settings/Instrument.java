package com.ascargon.rocketshow.settings;

import com.ascargon.rocketshow.composition.DefaultCompositionService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Defines an instrument played in a band.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
public class Instrument {

    private String uuid;

    // The name of the instrument
    private String name;

}
