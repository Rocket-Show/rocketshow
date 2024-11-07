package com.ascargon.rocketshow.lighting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * A response port we got from a OLA port list query.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class OlaPlugin {

    private Integer id;
    private String name;

}
