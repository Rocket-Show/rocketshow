package com.ascargon.rocketshow.lighting;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.UUID;

@XmlRootElement
public class LightingUniverse {

	private HashMap<Integer, Integer> universe = new HashMap<>();

	private final String uuid = String.valueOf(UUID.randomUUID());

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

	public HashMap<Integer, Integer> getUniverse() {
		return universe;
	}

	public void setUniverse(HashMap<Integer, Integer> universe) {
		this.universe = universe;
	}

}
