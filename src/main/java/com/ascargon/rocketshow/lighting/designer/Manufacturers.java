package com.ascargon.rocketshow.lighting.designer;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A list of Open Fixture Library manufacturers.
 *
 * @author Moritz A. Vieli
 */
@JsonIgnoreProperties({ "$schema" })
class Manufacturers {

    private Map<String, Manufacturer> manufacturers = new HashMap<>();

    public Map<String, Manufacturer> getManufacturers() {
        return manufacturers;
    }

    @JsonAnyGetter
    public Manufacturer getManufacturer(String shortName) {
        return manufacturers.get(shortName);
    }

    @JsonAnySetter
    public void setManufacturer(String name, Manufacturer manufacturer) {
        manufacturers.put(name, manufacturer);
    }

}
