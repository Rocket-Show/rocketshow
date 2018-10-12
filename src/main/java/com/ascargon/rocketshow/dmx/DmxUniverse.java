package com.ascargon.rocketshow.dmx;

import java.util.HashMap;
import java.util.UUID;

public class DmxUniverse {

	private HashMap<Integer, Integer> universe;
	
	final String uuid = String.valueOf(UUID.randomUUID());
	
	public DmxUniverse() {
		universe = new HashMap<Integer, Integer>();
	}
	
	@Override
	public boolean equals(Object object) {
		if (object instanceof DmxUniverse) {
			DmxUniverse dmxUniverse = (DmxUniverse) object;

			if (this.uuid.equals(dmxUniverse.uuid)) {
				return true;
			}
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
