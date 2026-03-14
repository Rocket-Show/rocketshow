package com.ascargon.rocketshow.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class ApiKey {

    private String uuid;
    private String key;

    @JsonIgnore
    private String keyHash;

    private String description;

}
