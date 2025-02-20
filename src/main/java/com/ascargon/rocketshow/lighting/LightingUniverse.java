package com.ascargon.rocketshow.lighting;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.UUID;

@XmlRootElement
@Getter
@Setter
public class LightingUniverse {

    private HashMap<Integer, Integer> universe = new HashMap<>();

    private final String uuid = String.valueOf(UUID.randomUUID());

    private String name;

    public LightingUniverse() {
        reset();
    }

    public void reset() {
        universe = new HashMap<>();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof LightingUniverse) {
            LightingUniverse lightingUniverse = (LightingUniverse) object;
            return this.uuid.equals(lightingUniverse.uuid);
        }

        return false;
    }

}
