package com.ascargon.rocketshow.settings;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

/**
 * An API key to access the devices API.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
@Getter
@Setter
public class ApiKeyHash {

    private String keyHash;
    private String description;

}
