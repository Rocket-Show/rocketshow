package com.ascargon.rocketshow.dmx;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.UUID;

@XmlRootElement
public class DmxUniverse {

	private HashMap<Integer, Integer> universe;

	private final String uuid = String.valueOf(UUID.randomUUID());

	public DmxUniverse() {
		universe = new HashMap<>();
	}

	@Override
	public boolean equals(Object object) {
		if (object instanceof DmxUniverse) {
			DmxUniverse dmxUniverse = (DmxUniverse) object;
			return this.uuid.equals(dmxUniverse.uuid);
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
