package com.ascargon.rocketshow.lighting;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@XmlRootElement
@Getter
@Setter
public class LightingUniverse {

    private String uuid = String.valueOf(UUID.randomUUID());
    private String name;
    private Integer olaUniverseId;
    private String olaOutputPortId;
    // OLA device name (e.g. "E1.31 (DMX over ACN) [192.168.1.200]") used to re-match the port ID after OLA restarts
    private String olaOutputPortDevice;

}
